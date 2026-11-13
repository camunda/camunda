/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.StampedLock;
import org.jspecify.annotations.Nullable;

/**
 * Committed state of one write-behind DB, shared by all its transaction contexts.
 *
 * <p>A commit publishes the transaction's versions to the active batch and queues the transaction.
 * One writer thread seals the active batch, so that commits go to a new one, and writes the sealed
 * batch to the storage as one atomic batch. So the storage always holds every transaction up to
 * some sequence and none after it, and a checkpoint at any moment is transaction-consistent.
 *
 * <p>The batches hold exactly the versions that the storage doesn't have yet. The read cache holds
 * flushed values only: the writer moves a sealed batch to it once the storage holds the batch, and
 * a read from the storage adds what it reads. So the cache never holds a value that is newer than
 * the storage.
 *
 * <p>{@link #publishLock} makes three things atomic: publishing a transaction, sealing the active
 * batch, and taking a read view (a storage cursor plus the batches). Nothing that holds it blocks
 * on anything else.
 *
 * @param <K> the key type
 * @param <V> the value as the store holds it, see {@link Backend}
 * @param <T> the caller's value objects, which reads fill in
 */
public final class WriteBehindStore<K extends Comparable<K>, V, T> implements AutoCloseable {

  private final Backend<K, V, T> backend;
  // the unflushed commits: commits go to the active batch, the writer seals it to flush it
  private volatile Batches<K, V> batches =
      new Batches<>(new ConcurrentSkipListMap<K, Version<V>>(), null);

  // odd while the writer flushes a sealed batch: a value that a reader loaded from the storage
  // during that time may be older than the one that the writer caches
  private volatile long flushEpoch;
  private final @Nullable Cache<K, Version<V>> cache;
  private final StampedLock publishLock = new StampedLock();

  private final AsyncFlusher<K, V> flusher;

  private final WriteBehindMetrics metrics = new WriteBehindMetrics();

  // guarded by publishLock's write side
  private long lastSeq;

  // written under publishLock's write side, read without it to fail fast
  private volatile boolean closed;

  private volatile long committedSeq;

  public WriteBehindStore(final Backend<K, V, T> backend, final WriteBehindConfiguration config) {
    this.backend = backend;
    cache =
        config.cacheMaxBytes() > 0
            ? Caffeine.newBuilder()
                .maximumWeight(config.cacheMaxBytes())
                .<K, Version<V>>weigher((key, version) -> backend.weigh(key, version.value()))
                .build()
            : null;
    flusher =
        new AsyncFlusher<>(
            backend, config, metrics, this::ensureUsable, this::sealBatch, this::releaseBatch);
    flusher.start();
  }

  /**
   * Publishes a transaction and queues it for the writer. Waits first while too many bytes are
   * unflushed. A transaction above the limit on its own still commits once the queue is empty.
   *
   * @param values the new value per key, or null for a delete; the store takes ownership and nobody
   *     may change them afterwards
   */
  public void commit(final K[] keys, final @Nullable V[] values) {
    ensureUsable();
    flusher.awaitCapacity();

    @SuppressWarnings("unchecked")
    final Version<V>[] versions = new Version[keys.length];
    final long stamp = publishLock.writeLock();
    try {
      ensureUsable();
      final long seq = ++lastSeq;
      final var active = batches.active();
      long bytes = 0;
      for (int i = 0; i < keys.length; i++) {
        final var version = new Version<>(values[i], seq, active.get(keys[i]));
        versions[i] = version;
        final var counters = metrics.of(backend.groupOf(keys[i]));
        (values[i] == null ? counters.committedDeletes : counters.committedPuts).increment();
        bytes += backend.weigh(keys[i], values[i]);
        active.put(keys[i], version);
      }
      flusher.enqueue(seq, keys, versions, bytes);
      committedSeq = seq;
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * Reads the latest committed value, or null if there is none. A value read from the storage is
   * cached.
   *
   * <p>The result is shared with the store: callers must copy it before handing it out.
   */
  public @Nullable V get(final K key) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return inMemory.value();
    }
    return readStorage(key);
  }

  /**
   * The unflushed version of a key as of the last whole commit, or else the cached one; null if the
   * store has neither.
   *
   * <p>A commit puts its versions into the active batch one by one and sets {@link #committedSeq}
   * after the last one. So the sequence is read first, and a version of a later commit, which may
   * be half published, is skipped for the one it replaced. The cache only holds flushed values,
   * which are whole commits.
   */
  private @Nullable Version<V> lookupInMemory(final K key) {
    final long seq = committedSeq;
    final var unflushed = batches.getAsOf(key, seq);
    if (unflushed != null) {
      metrics.of(backend.groupOf(key)).dirtyHits.increment();
      return unflushed;
    }
    final var cached = cache == null ? null : cache.getIfPresent(key);
    if (cached != null) {
      metrics.of(backend.groupOf(key)).cacheHits.increment();
    }
    return cached;
  }

  /**
   * Reads the latest committed value from the storage, or returns null if there is none. A value
   * read from the storage is cached; a missing one is not, as a cached absence of short-lived state
   * would only fill the cache.
   */
  private @Nullable V readStorage(final K key) {
    // A batch only leaves the store after the storage holds it and the cache has it, so a key that
    // isn't in a batch has its latest flushed value in the storage. A value that is loaded while
    // the writer flushes may be older than the one it caches, so it isn't cached then.
    final long epoch = flushEpoch;
    final var value = backend.get(key);
    countStorageGet(key, value != null);
    if (value != null && cache != null && (epoch & 1) == 0 && flushEpoch == epoch) {
      cacheLoaded(cache, key, new Version<>(value, 0), epoch);
    }
    return value;
  }

  /**
   * Caches a value that a read loaded from the storage during {@code epoch}, unless a flush started
   * since. A flush may start right after the epoch check, and cache a newer value or evict a
   * deleted key; a plain put would then overwrite that value or bring the key back. So the loaded
   * value only goes into an empty slot, and leaves again if a flush started meanwhile; anything the
   * flush put in its place stays.
   */
  private void cacheLoaded(
      final Cache<K, Version<V>> cache, final K key, final Version<V> loaded, final long epoch) {
    final var map = cache.asMap();
    if (map.putIfAbsent(key, loaded) == null && flushEpoch != epoch) {
      map.remove(key, loaded);
    }
  }

  /** Whether the key has a committed value. Doesn't decode or cache what it reads. */
  boolean exists(final K key) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return !inMemory.isTombstone();
    }
    final var found = backend.exists(key);
    countStorageGet(key, found);
    return found;
  }

  private void countStorageGet(final K key, final boolean found) {
    final var counters = metrics.of(backend.groupOf(key));
    counters.storageGets.increment();
    if (found) {
      counters.storageGetsFound.increment();
    }
  }

  /**
   * Takes a point-in-time view of the committed state for the keys of a range: a storage cursor and
   * the unflushed versions of the same instant, taken together. The view reads the unflushed
   * versions from the batches when it is used, so opening it copies nothing.
   */
  View<K, V, T> openView(final KeyRange<K> range) {
    ensureUsable();
    final var from = range.from();
    final var to = range.to();
    final long stamp = publishLock.readLock();
    try {
      // the batches first: what is flushed after this is in the batches we hold, what was flushed
      // before is in the storage that the cursor reads
      final var current = batches;
      final var storageCursor = backend.newCursor();
      metrics.iteratorsOpened.increment();
      metrics.of(backend.groupOf(from)).iterators.increment();
      final var sealed = current.sealed();
      final var dirtyRange =
          new DirtyRange<>(
              inRange(current.active(), from, to),
              sealed == null ? null : inRange(sealed, from, to),
              lastSeq);
      return new View<>(storageCursor, dirtyRange, lastSeq);
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  private static <K extends Comparable<K>, V> NavigableMap<K, Version<V>> inRange(
      final NavigableMap<K, Version<V>> batch, final K from, final @Nullable K to) {
    return to == null ? batch.tailMap(from, true) : batch.subMap(from, true, to, false);
  }

  /**
   * Registers the store's meters in the registry, tagged with {@code storeName} so that several
   * stores can share a registry. Call it again to pick up column families that were first used
   * since; meters that exist are kept.
   */
  public void exportMetrics(final MeterRegistry registry, final String storeName) {
    metrics.export(registry, storeName);
    final var tags = Tags.of("store", storeName);
    Gauge.builder("zeebe.writebehind.pending.bytes", flusher, AsyncFlusher::pendingBytes)
        .baseUnit("bytes")
        .tags(tags)
        .register(registry);
    Gauge.builder(
            "zeebe.writebehind.pending.transactions",
            this,
            store -> store.committedSeq - store.flusher.flushedSeq())
        .tags(tags)
        .register(registry);
    Gauge.builder(
            "zeebe.writebehind.pending.age",
            flusher,
            f -> f.oldestUnflushedAgeNanos() / 1_000_000_000d)
        .baseUnit("seconds")
        .tags(tags)
        .register(registry);
    if (cache != null) {
      Gauge.builder("zeebe.writebehind.cache.entries", cache, Cache::estimatedSize)
          .tags(tags)
          .register(registry);
    }
  }

  Backend<K, V, T> backend() {
    return backend;
  }

  WriteBehindMetrics metrics() {
    return metrics;
  }

  /**
   * Waits until the storage holds every transaction committed before this call. Fails if the writer
   * fails or the flush takes longer than the configured timeout.
   */
  public void awaitFlushed() {
    // under the lock that commits hold to publish: a commit that is half published is visible to
    // readers already, so it must be part of the target; with the lock, no commit is half published
    final long target;
    final long stamp = publishLock.readLock();
    try {
      target = committedSeq;
    } finally {
      publishLock.unlockRead(stamp);
    }
    flusher.awaitFlushed(target);
  }

  /** Throws if the writer failed or the store is closed, so that callers fail fast. */
  void ensureUsable() {
    final var cause = flusher.failure();
    if (cause != null) {
      throw new IllegalStateException(
          "Expected the write-behind writer to keep the storage up to date, but it failed", cause);
    }
    if (closed) {
      throw new IllegalStateException(
          "Expected the write-behind store to be open, but it is closed");
    }
  }

  /** Stops accepting commits, lets the writer flush what is queued, and stops it. */
  @Override
  public void close() {
    final long stamp = publishLock.writeLock();
    try {
      if (closed) {
        return;
      }
      closed = true;
      flusher.requestStop();
    } finally {
      publishLock.unlockWrite(stamp);
    }
    flusher.awaitStopped();
  }

  /**
   * Called by the writer: seals the active batch, so that commits go to a new one. {@code drain}
   * takes the queued transactions of the sealed batch, under the lock that commits hold to publish,
   * so that the batch and the queue hold the same commits.
   */
  private NavigableMap<K, Version<V>> sealBatch(final Runnable drain) {
    final long stamp = publishLock.writeLock();
    try {
      final var current = batches;
      drain.run();
      flushEpoch++;
      batches = new Batches<>(new ConcurrentSkipListMap<>(), current.active());
      return current.active();
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * Called by the writer after the storage holds the sealed batch: moves its values to the cache,
   * then drops the batch. A reader finds a value in the batch or in the cache, never in neither.
   * The cache only gets flushed values, so it never holds one that is newer than the storage.
   */
  private void releaseBatch(final NavigableMap<K, Version<V>> sealed) {
    if (cache != null) {
      // without the history that the versions of the batch keep for readers of older commits; a
      // deleted key is evicted, as a cached tombstone of short-lived state would only fill the
      // cache, and a read of the key finds nothing in the storage
      sealed.forEach(
          (key, version) -> {
            if (version.isTombstone()) {
              cache.invalidate(key);
            } else {
              cache.put(key, new Version<>(version.value(), version.seq()));
            }
          });
    }
    batches = new Batches<>(batches.active(), null);
    flushEpoch++;
  }

  /** The unflushed commits: the active batch, and the sealed one that the writer is flushing. */
  private record Batches<K extends Comparable<K>, V>(
      NavigableMap<K, Version<V>> active, @Nullable NavigableMap<K, Version<V>> sealed) {

    /**
     * The latest version of the key up to the commit with the given sequence: from the active
     * batch, or else from the sealed one. Null if neither has one, then the cache or the storage
     * has the latest value up to that commit.
     */
    @Nullable Version<V> getAsOf(final K key, final long seq) {
      final var active = this.active.get(key);
      final var fromActive = active == null ? null : active.asOf(seq);
      if (fromActive != null || sealed == null) {
        return fromActive;
      }
      final var fromSealed = sealed.get(key);
      return fromSealed == null ? null : fromSealed.asOf(seq);
    }
  }

  /**
   * A storage cursor plus the unflushed versions of the same instant, restricted to a range.
   *
   * @param viewSeq the sequence of the last commit that the view includes
   */
  record View<K extends Comparable<K>, V, T>(
      Backend.Cursor<K, T> storageCursor, DirtyRange<K, V> dirty, long viewSeq) {}
}
