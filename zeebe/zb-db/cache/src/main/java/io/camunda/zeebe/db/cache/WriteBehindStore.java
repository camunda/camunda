/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.core.internal.statistics.DefaultStatisticsService;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the committed state that the storage doesn't have yet, and hands it to the writer.
 *
 * <p>A commit publishes all of its versions to the DirtyIndex under {@code publishLock}'s write
 * side, puts them into the read cache, then queues the whole transaction for the writer. Readers
 * validate against {@code publishLock}, so they see all of a transaction or none of it. {@code
 * commitLock} serializes committers from start to finish, so the queue holds transactions in commit
 * order, and the cache gets them in the same order.
 *
 * <p>Point reads check the DirtyIndex before the cache: it holds every unflushed version, while the
 * cache can briefly hold an older one.
 */
final class WriteBehindStore<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(WriteBehindStore.class);
  private static final int OPTIMISTIC_READ_ATTEMPTS = 3;

  private final WriteBehindConfiguration configuration;
  private final int optimisticReadAttempts;
  private final DirtyIndex dirtyIndex = new DirtyIndex();
  private final ReentrantLock commitLock = new ReentrantLock();
  private final StampedLock publishLock = new StampedLock();
  private final ReentrantLock flushLock = new ReentrantLock();
  private final Condition flushed = flushLock.newCondition();
  private final WriteBehindMetrics metrics;
  private final StorageLoader<ColumnFamilyNames> loader;
  private final TransactionWriter<ColumnFamilyNames> writer;
  private final CacheManager cacheManager;
  private final Cache<CacheKey, CachedValue> cache;

  private long lastSeq; // guarded by commitLock
  private final AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
  private final List<Consumer<Throwable>> failureListeners = new CopyOnWriteArrayList<>();

  WriteBehindStore(
      final ZeebeDb<ColumnFamilyNames> storage, final WriteBehindConfiguration configuration) {
    this(storage, configuration, OPTIMISTIC_READ_ATTEMPTS);
  }

  /**
   * @param optimisticReadAttempts how often a point read tries without a lock before it reads under
   *     {@code publishLock}'s read side
   */
  WriteBehindStore(
      final ZeebeDb<ColumnFamilyNames> storage,
      final WriteBehindConfiguration configuration,
      final int optimisticReadAttempts) {
    this.configuration = configuration;
    this.optimisticReadAttempts = optimisticReadAttempts;
    metrics = new WriteBehindMetrics(storage.getMeterRegistry());
    loader = new StorageLoader<>(dirtyIndex, metrics);
    writer =
        new TransactionWriter<>(
            storage,
            dirtyIndex,
            publishLock,
            configuration,
            metrics,
            this::signalFlushed,
            this::fail);

    final var statistics = new DefaultStatisticsService();
    cacheManager = CacheManagerBuilder.newCacheManagerBuilder().using(statistics).build(true);
    cache =
        cacheManager.createCache(
            "state",
            CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    CacheKey.class,
                    CachedValue.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(configuration.cacheMaxBytes(), MemoryUnit.B))
                .withLoaderWriter(loader));

    metrics.registerReadCache(statistics.getCacheStatistics("state"));
    metrics.registerPending(
        dirtyIndex::size, () -> writer.lastQueuedSeq() - writer.flushedSeq(), writer::queuedBytes);
  }

  /**
   * Publishes {@code writes} as one transaction and queues it for the storage. A transaction
   * without writes commits without taking a lock.
   *
   * <p>The caller must keep its writes until this returns, so that a retried commit applies them
   * again.
   */
  void commit(final Map<CacheKey, Op> writes) {
    if (writes.isEmpty()) {
      return;
    }

    final long lockStart = System.nanoTime();
    commitLock.lock();
    try {
      final long lockWaitNanos = System.nanoTime() - lockStart;
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
        // before the writer can write them and clean them out of the DirtyIndex, after which the
        // cache must not still hold older versions
        versions.forEach(version -> cache.put(version.getKey(), version.getValue()));
        writer.enqueue(CommittedTransaction.of(seq, versions));
      } catch (final RuntimeException e) {
        // the published versions may never reach the storage, or the cache may keep older ones
        fail(e);
        throw e;
      }
      metrics.observeCommit(versions.size(), lockWaitNanos);
      failIfFailed();
    } finally {
      commitLock.unlock();
    }
  }

  /**
   * Returns a copy of the committed value of {@code key}, or {@code null} if there is none. A cache
   * miss loads from the storage through {@code reader}, the caller's own column families.
   *
   * <p>The cache may keep {@code key}, so it must not change afterwards.
   */
  byte @Nullable [] get(final CacheKey key, final StorageColumnFamilies<ColumnFamilyNames> reader) {
    final var value = committedValue(key, reader);
    return value == null ? null : value.clone();
  }

  /** Like {@link #get}, without copying the value. */
  boolean exists(final CacheKey key, final StorageColumnFamilies<ColumnFamilyNames> reader) {
    return committedValue(key, reader) != null;
  }

  /**
   * Copies the unflushed versions of a key range for a scan, under {@code publishLock}'s read side.
   * The returned overlay keeps holding it, so nothing is published or cleaned up until the storage
   * has fixed its view of the same range. Release it at the storage's first callback, or when the
   * storage scan returns without one.
   *
   * <p>Every unflushed version is then in the copy, and every version cleaned up before it is in
   * the storage's view, so the two together show the committed state at one instant.
   */
  ScanOverlay beginScan(
      final EnumValue columnFamily,
      final @Nullable CacheKey from,
      final boolean fromInclusive,
      final @Nullable CacheKey to,
      final boolean toInclusive) {
    final long stamp = publishLock.readLock();
    try {
      return new ScanOverlay(
          dirtyIndex.copy(columnFamily, from, fromInclusive, to, toInclusive), stamp);
    } catch (final RuntimeException e) {
      publishLock.unlockRead(stamp);
      throw e;
    }
  }

  /**
   * Waits until the storage holds every transaction committed before this call. It doesn't hold
   * commits back, and the writer writes those transactions at once instead of waiting out {@code
   * maxWriteDelay}.
   *
   * @throws ZeebeDbException if the writer doesn't catch up within {@code snapshotCatchUpTimeout}
   */
  void catchUp() {
    failIfFailed();
    final long start = System.nanoTime();
    final long target = writer.lastQueuedSeq();
    writer.flushUpTo(target);

    final var timeout = configuration.snapshotCatchUpTimeout();
    final long deadline = System.nanoTime() + timeout.toNanos();
    flushLock.lock();
    try {
      long remaining = timeout.toNanos();
      while (writer.flushedSeq() < target && failure.get() == null && remaining > 0) {
        flushed.awaitNanos(remaining);
        remaining = deadline - System.nanoTime();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ZeebeDbException("Interrupted while waiting for the writer to catch up", e);
    } finally {
      flushLock.unlock();
    }
    metrics.observeSnapshotCatchUp(System.nanoTime() - start);

    failIfFailed();
    if (writer.flushedSeq() < target) {
      throw new ZeebeDbException(
          "Expected the writer to write every committed transaction within %s, but it didn't"
              .formatted(timeout),
          new TimeoutException());
    }
  }

  /** Returns why the store failed, or {@code null} if it hasn't. */
  @Nullable Throwable failure() {
    return failure.get();
  }

  /** Calls {@code listener} once the store fails. */
  void addFailureListener(final Consumer<Throwable> listener) {
    failureListeners.add(listener);
  }

  WriteBehindMetrics metrics() {
    return metrics;
  }

  /** Returns the key and value bytes of the transactions the storage doesn't have yet. */
  long pendingBytes() {
    return writer.queuedBytes();
  }

  @Override
  public void close() {
    writer.close();
    cacheManager.close();
  }

  private byte @Nullable [] committedValue(
      final CacheKey key, final StorageColumnFamilies<ColumnFamilyNames> reader) {
    for (int attempt = 0; attempt < optimisticReadAttempts; attempt++) {
      final long stamp = publishLock.tryOptimisticRead();
      if (stamp != 0) {
        final var unflushed = dirtyIndex.get(key);
        final CachedValue value;
        if (unflushed != null) {
          value = unflushed;
        } else {
          metrics.observeCacheGet();
          value = loader.readThrough(reader, () -> cache.get(key));
        }
        if (publishLock.validate(stamp)) {
          if (unflushed != null) {
            metrics.observeUnflushedRead();
          }
          return value.bytes();
        }
      }
      metrics.observeReadRetry();
    }

    // without the cache, so the lock isn't held while waiting on one of the cache's map bins
    metrics.observeLockedRead();
    final long stamp = publishLock.readLock();
    try {
      final var unflushed = dirtyIndex.get(key);
      return unflushed != null ? unflushed.bytes() : reader.get(key);
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  private void awaitPendingBytesWithinLimit() {
    flushLock.lock();
    try {
      final long start = System.nanoTime();
      boolean waited = false;
      while (writer.queuedBytes() > configuration.maxPendingBytes() && failure.get() == null) {
        waited = true;
        flushed.awaitUninterruptibly();
      }
      if (waited) {
        metrics.observeBackpressureWait(System.nanoTime() - start);
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

  /**
   * Fails the store, once: commits and snapshots fail fast from then on, and everything waiting on
   * the writer aborts. Reads keep working, since the DirtyIndex and the storage still hold the
   * exact committed state.
   */
  private void fail(final Throwable cause) {
    if (failure.compareAndSet(null, cause)) {
      LOG.error("The write-behind store failed; commits and snapshots fail from now on", cause);
      metrics.observeFailure();
      signalFlushed();
      failureListeners.forEach(listener -> listener.accept(cause));
    }
  }

  private void failIfFailed() {
    final var cause = failure.get();
    if (cause != null) {
      throw new UnrecoverableException(
          "Expected the write-behind store to be usable, but it failed earlier", cause);
    }
  }

  /** The unflushed versions a scan merges in, and the read lock it holds until released. */
  final class ScanOverlay {

    private final NavigableMap<CacheKey, CachedValue> unflushed;
    private final long startNanos = System.nanoTime();
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
        metrics.observeScanHold(System.nanoTime() - startNanos);
      }
    }
  }
}
