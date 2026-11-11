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
import java.util.NavigableMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.jspecify.annotations.Nullable;

/**
 * Holds the committed state that the backend doesn't have yet, and hands it to the writer.
 *
 * <p>A commit publishes all of its versions to the DirtyIndex under {@code publishLock}'s write
 * side, queues the whole transaction for the writer, then puts its versions into the read cache.
 * Readers validate against {@code publishLock}, so they see all of a transaction or none of it.
 * {@code commitLock} serializes committers from start to finish, so the queue holds transactions in
 * commit order, and the cache gets them in the same order.
 *
 * <p>Point reads check the DirtyIndex before the cache: it holds every unflushed version, while the
 * cache can briefly hold an older one.
 */
final class WriteBehindStore<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements AutoCloseable {

  private static final int OPTIMISTIC_READ_ATTEMPTS = 3;

  private final WriteBehindConfiguration configuration;
  private final int optimisticReadAttempts;
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final ReentrantLock commitLock = new ReentrantLock();
  private final StampedLock publishLock = new StampedLock();
  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final BackendLoader<ColumnFamilyNames> loader;
  private final TransactionWriter<ColumnFamilyNames> writer;
  private final CacheManager cacheManager;
  private final Cache<CacheKey, CachedValue> cache;

  private long lastSeq; // guarded by commitLock
  private volatile @Nullable Throwable failure;

  WriteBehindStore(
      final ZeebeDb<ColumnFamilyNames> backend, final WriteBehindConfiguration configuration) {
    this(backend, configuration, OPTIMISTIC_READ_ATTEMPTS);
  }

  /**
   * @param optimisticReadAttempts how often a point read tries without a lock before it reads under
   *     {@code publishLock}'s read side
   */
  WriteBehindStore(
      final ZeebeDb<ColumnFamilyNames> backend,
      final WriteBehindConfiguration configuration,
      final int optimisticReadAttempts) {
    this.configuration = configuration;
    this.optimisticReadAttempts = optimisticReadAttempts;
    loader = new BackendLoader<>(dirtyIndex);
    writer =
        new TransactionWriter<>(
            backend, dirtyIndex, publishLock, configuration, this::signalFlushed, this::fail);

    cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build(true);
    cache =
        cacheManager.createCache(
            "state",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    CacheKey.class,
                    CachedValue.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(configuration.cacheMaxBytes(), MemoryUnit.B))
                .withLoaderWriter(loader));
  }

  /**
   * Publishes {@code writes} as one transaction and queues it for the backend. A transaction
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
      } finally {
        publishLock.unlockWrite(stamp);
      }

      try {
        writer.enqueue(CommittedTransaction.of(versions));
        versions.forEach(version -> cache.put(version.getKey(), version.getValue()));
      } catch (final RuntimeException e) {
        // the published versions may never reach the backend, or the cache may keep older ones
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
    final var value = committedValue(key, reader);
    return value == null ? null : value.clone();
  }

  /** Like {@link #get}, without copying the value. */
  boolean exists(final CacheKey key, final PassThroughColumnFamilies<ColumnFamilyNames> reader) {
    return committedValue(key, reader) != null;
  }

  /**
   * Copies the unflushed versions of a key range for a scan, under {@code publishLock}'s read side.
   * The returned overlay keeps holding it, so nothing is published or cleaned up until the backend
   * has fixed its view of the same range. Release it at the backend's first callback, or when the
   * backend scan returns without one.
   *
   * <p>Every unflushed version is then in the copy, and every version cleaned up before it is in
   * the backend's view, so the two together show the committed state at one instant.
   */
  ScanOverlay beginScan(
      final EnumValue columnFamily,
      final @Nullable CacheKey from,
      final boolean fromInclusive,
      final @Nullable CacheKey to,
      final boolean toInclusive) {
    failIfFailed();
    final long stamp = publishLock.readLock();
    try {
      return new ScanOverlay(
          dirtyIndex.copy(columnFamily, from, fromInclusive, to, toInclusive), stamp);
    } catch (final RuntimeException e) {
      publishLock.unlockRead(stamp);
      throw e;
    }
  }

  /** Returns the key and value bytes of the transactions the backend doesn't have yet. */
  long pendingBytes() {
    return writer.queuedBytes();
  }

  @Override
  public void close() {
    writer.close();
    cacheManager.close();
  }

  private byte @Nullable [] committedValue(
      final CacheKey key, final PassThroughColumnFamilies<ColumnFamilyNames> reader) {
    failIfFailed();

    for (int attempt = 0; attempt < optimisticReadAttempts; attempt++) {
      final long stamp = publishLock.tryOptimisticRead();
      if (stamp == 0) {
        continue;
      }

      final var value = lookup(key, reader);
      if (publishLock.validate(stamp)) {
        return value.bytes();
      }
    }

    // without the cache, so the lock isn't held while waiting on one of the cache's map bins
    final long stamp = publishLock.readLock();
    try {
      final var unflushed = dirtyIndex.get(key);
      return unflushed != null ? unflushed.bytes() : reader.get(key);
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  private CachedValue lookup(
      final CacheKey key, final PassThroughColumnFamilies<ColumnFamilyNames> reader) {
    final var unflushed = dirtyIndex.get(key);
    if (unflushed != null) {
      return unflushed;
    }
    return loader.readThrough(reader, () -> cache.get(key));
  }

  private void awaitPendingBytesWithinLimit() {
    flushLock.lock();
    try {
      while (writer.queuedBytes() > configuration.maxPendingBytes() && failure == null) {
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

  /** The unflushed versions a scan merges in, and the read lock it holds until released. */
  final class ScanOverlay {

    private final NavigableMap<CacheKey, CachedValue> unflushed;
    private long stamp;

    private ScanOverlay(final NavigableMap<CacheKey, CachedValue> unflushed, final long stamp) {
      this.unflushed = unflushed;
      this.stamp = stamp;
    }

    NavigableMap<CacheKey, CachedValue> unflushed() {
      return unflushed;
    }

    /** Lets publication and cleanup go on; does nothing if already released. */
    void release() {
      if (stamp != 0) {
        publishLock.unlockRead(stamp);
        stamp = 0;
      }
    }
  }
}
