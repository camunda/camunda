/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.WriteBehindConfigurationBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.jspecify.annotations.Nullable;

/**
 * Holds the committed state that the backend doesn't have yet, and hands it to EhCache's
 * write-behind queue.
 *
 * <p>A commit publishes all of its versions to the DirtyIndex under {@code publishLock}'s write
 * side, then enqueues them. Readers validate against {@code publishLock}, so they see all of a
 * transaction or none of it. {@code commitLock} serializes committers from start to finish, so the
 * enqueue order matches the publication order.
 *
 * <p>Point reads check the DirtyIndex before the cache: it holds every unflushed version, while the
 * cache can briefly hold an older one or lose a pending op.
 */
final class WriteBehindStore<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements AutoCloseable {

  private static final int OPTIMISTIC_READ_ATTEMPTS = 3;

  private final WriteBehindConfiguration configuration;
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final ReentrantLock commitLock = new ReentrantLock();
  private final StampedLock publishLock = new StampedLock();
  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final BackendLoaderWriter<ColumnFamilyNames> loaderWriter;
  private final CacheManager cacheManager;
  private final Cache<CacheKey, CachedValue> cache;

  private long lastSeq; // guarded by commitLock
  private volatile long lastPublishedSeq;
  private volatile @Nullable Throwable failure;

  WriteBehindStore(
      final ZeebeDb<ColumnFamilyNames> backend, final WriteBehindConfiguration configuration) {
    this.configuration = configuration;
    loaderWriter =
        new BackendLoaderWriter<>(
            backend, dirtyIndex, publishLock, () -> lastPublishedSeq, this::signalFlushed);

    final var batching =
        WriteBehindConfigurationBuilder.newBatchedWriteBehindConfiguration(
                configuration.maxWriteDelay().toMillis(),
                TimeUnit.MILLISECONDS,
                configuration.batchSize())
            .queueSize(configuration.queueSize())
            .concurrencyLevel(configuration.concurrencyLevel());
    final var writeBehind =
        configuration.coalescing() ? batching.enableCoalescing() : batching.disableCoalescing();

    cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
    cache =
        cacheManager.createCache(
            "write-behind",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    CacheKey.class,
                    CachedValue.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(configuration.cacheMaxBytes(), MemoryUnit.B))
                .withLoaderWriter(loaderWriter)
                .withService(writeBehind.build()));
  }

  /**
   * Publishes {@code writes} as one transaction and enqueues them for the backend. A transaction
   * without writes commits without taking a lock.
   *
   * <p>The caller must keep its writes until this returns, so that a retried commit applies them
   * again.
   */
  void commit(final Map<CacheKey, Op> writes) {
    if (writes.isEmpty()) {
      failIfFailed();
      return;
    }

    commitLock.lock();
    try {
      failIfFailed();
      awaitPendingBytesWithinLimit();

      final long seq = ++lastSeq;
      final List<Map.Entry<CacheKey, CachedValue>> versions = new ArrayList<>(writes.size());
      writes.forEach((key, op) -> versions.add(Map.entry(key, new CachedValue(op.bytes(), seq))));

      final long stamp = publishLock.writeLock();
      try {
        versions.forEach(version -> dirtyIndex.put(version.getKey(), version.getValue()));
        lastPublishedSeq = seq;
      } finally {
        publishLock.unlockWrite(stamp);
      }

      try {
        versions.forEach(version -> cache.put(version.getKey(), version.getValue()));
      } catch (final RuntimeException e) {
        // the published versions can no longer reach the backend
        fail(e);
        throw e;
      }
      failIfFailed();
    } finally {
      commitLock.unlock();
    }
  }

  /**
   * Returns a copy of the committed value of {@code key}, or {@code null} if there is none. A cache
   * miss loads from the backend through {@code reader}, the caller's own column families.
   *
   * <p>The cache may keep {@code key}, so it must not change afterwards.
   */
  byte @Nullable [] get(
      final CacheKey key, final PassThroughColumnFamilies<ColumnFamilyNames> reader) {
    failIfFailed();

    for (int attempt = 0; attempt < OPTIMISTIC_READ_ATTEMPTS; attempt++) {
      final long stamp = publishLock.tryOptimisticRead();
      if (stamp == 0) {
        continue;
      }

      final var value = lookup(key, reader);
      if (publishLock.validate(stamp)) {
        return copy(value.bytes());
      }
    }

    // Without the cache: a committer blocked on a full queue holds the key's map bin in the cache,
    // and the writer needs the write side of publishLock before it can drain that queue.
    final long stamp = publishLock.readLock();
    try {
      final var unflushed = dirtyIndex.get(key);
      return unflushed != null ? copy(unflushed.bytes()) : reader.get(key);
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  @Override
  public void close() {
    cacheManager.close();
  }

  private CachedValue lookup(
      final CacheKey key, final PassThroughColumnFamilies<ColumnFamilyNames> reader) {
    final var unflushed = dirtyIndex.get(key);
    if (unflushed != null) {
      return unflushed;
    }
    return loaderWriter.readThrough(reader, () -> cache.get(key));
  }

  private void awaitPendingBytesWithinLimit() {
    flushLock.lock();
    try {
      while (dirtyIndex.bytes() > configuration.maxPendingBytes() && failure == null) {
        flushed.awaitUninterruptibly();
      }
    } finally {
      flushLock.unlock();
    }
    failIfFailed();
  }

  private void signalFlushed() {
    flushLock.lock();
    try {
      flushed.signalAll();
    } finally {
      flushLock.unlock();
    }
  }

  private void fail(final Throwable cause) {
    failure = cause;
    signalFlushed();
  }

  private void failIfFailed() {
    final var cause = failure;
    if (cause != null) {
      throw new UnrecoverableException(
          "Expected the write-behind store to be usable, but it failed earlier", cause);
    }
  }

  private static byte @Nullable [] copy(final byte @Nullable [] bytes) {
    return bytes == null ? null : bytes.clone();
  }
}
