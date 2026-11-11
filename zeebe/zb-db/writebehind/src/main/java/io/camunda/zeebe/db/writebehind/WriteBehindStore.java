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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.StampedLock;
import org.agrona.collections.Long2ObjectHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Committed state of one write-behind DB, shared by all its transaction contexts.
 *
 * <p>A commit publishes the transaction's versions to the dirty index and queues the transaction.
 * One writer thread takes the queued transactions in commit order and writes each group of them to
 * the storage as one atomic batch. So the storage always holds every transaction up to some
 * sequence and none after it, and a checkpoint at any moment is transaction-consistent.
 *
 * <p>Values are held as objects, never as bytes: the dirty index holds exactly the versions that
 * the storage doesn't have yet, and the read cache holds values decoded from the storage. A commit
 * invalidates the keys it writes. Only the writer thread encodes values.
 *
 * <p>{@link #publishLock} makes three things atomic: publishing a transaction, removing a flushed
 * group from the dirty index, and taking a read view (a storage cursor plus a copy of the dirty
 * index). Nothing that holds it blocks on anything else.
 */
public final class WriteBehindStore<K extends Comparable<K>, V> implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.db.writebehind");
  private static final int MAX_DISCARDED_LOADS = 3;

  private final Backend<K, V> backend;
  private final WriteBehindConfiguration config;
  private final ConcurrentSkipListMap<K, Version<V>> dirty = new ConcurrentSkipListMap<>();
  private final @Nullable Cache<K, Version<V>> cache;
  private final StampedLock publishLock = new StampedLock();

  // built in the constructor and never changed, so readers need no lock
  private final Long2ObjectHashMap<KeyIndex<K>> keyIndexes = new Long2ObjectHashMap<>();

  private final AsyncFlusher<K, V> flusher;

  private final WriteBehindMetrics metrics = new WriteBehindMetrics();

  // guarded by publishLock's write side
  private long lastSeq;

  // written under publishLock's write side, read without it to fail fast
  private volatile boolean closed;

  private volatile long committedSeq;

  public WriteBehindStore(final Backend<K, V> backend, final WriteBehindConfiguration config) {
    this.backend = backend;
    this.config = config;
    cache =
        config.cacheMaxBytes() > 0
            ? Caffeine.newBuilder()
                .maximumWeight(config.cacheMaxBytes())
                .<K, Version<V>>weigher((key, version) -> backend.weigh(key, version.value()))
                .build()
            : null;
    for (final long columnFamily : config.keyIndexColumnFamilies()) {
      keyIndexes.put(
          columnFamily, new KeyIndex<>(columnFamily, config.keyIndexPolicy(columnFamily)));
    }
    flusher = new AsyncFlusher<>(backend, config, metrics, this::ensureUsable, this::removeFlushed);
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
      long bytes = 0;
      for (int i = 0; i < keys.length; i++) {
        final var version = new Version<>(values[i], seq);
        versions[i] = version;
        final var counters = metrics.of(backend.groupOf(keys[i]));
        (values[i] == null ? counters.committedDeletes : counters.committedPuts).increment();
        bytes += backend.weigh(keys[i], values[i]);
        dirty.put(keys[i], version);
        if (cache != null) {
          cache.invalidate(keys[i]);
        }
      }
      updateKeyIndexes(keys, values, seq);
      flusher.enqueue(seq, keys, versions, bytes);
      committedSeq = seq;
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * Reads the latest committed value, or null if there is none. A value read from the storage is
   * decoded into a new instance of {@code prototype}'s class and cached.
   *
   * <p>The result is shared with the store: callers must copy it before handing it out.
   */
  public @Nullable V get(final K key, final V prototype) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return inMemory.value();
    }
    if (absentFromKeyIndex(key)) {
      metrics.of(backend.groupOf(key)).keyIndexAbsent.increment();
      return null;
    }
    return readStorage(key, prototype);
  }

  /** The unflushed version of a key, or else the cached one; null if the store has neither. */
  @Nullable Version<V> lookupInMemory(final K key) {
    final var unflushed = dirty.get(key);
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
   * Reads the latest committed value from the storage, or returns null if there is none, unless a
   * newer version is unflushed. A value read from the storage is decoded into a new instance of
   * {@code prototype}'s class and cached.
   */
  @Nullable V readStorage(final K key, final V prototype) {
    // Under the read lock no commit can publish this key and no flush can remove it, so if it isn't
    // in the dirty index now, the storage has its latest value and nothing can make the cached copy
    // stale before a later commit invalidates it.
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return latest.value();
      }
      final var value = backend.get(key, prototype);
      countStorageGet(key, value != null);
      if (cache != null) {
        cache.put(key, new Version<>(value, 0));
      }
      return value;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /** Whether the key has a committed value. Doesn't decode or cache what it reads. */
  boolean exists(final K key) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return !inMemory.isTombstone();
    }
    if (absentFromKeyIndex(key)) {
      metrics.of(backend.groupOf(key)).keyIndexAbsent.increment();
      return false;
    }
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return !latest.isTombstone();
      }
      final var found = backend.exists(key);
      countStorageGet(key, found);
      return found;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  private void countStorageGet(final K key, final boolean found) {
    final var counters = metrics.of(backend.groupOf(key));
    counters.storageGets.increment();
    if (found) {
      counters.storageGetsFound.increment();
    }
  }

  /**
   * Whether a loaded key index shows that the key has no committed value. A commit changes the
   * dirty index and the key index in one critical section, so a reader that missed the dirty index
   * before a commit and then sees the new key set finds the key, and one that sees the old key set
   * gets the state from before the commit. A key in the index gives no answer, as it holds no
   * values, and neither does a key outside the window.
   */
  private boolean absentFromKeyIndex(final K key) {
    if (keyIndexes.isEmpty()) {
      return false;
    }
    final var index = keyIndexes.get(backend.groupOf(key));
    if (index == null) {
      return false;
    }
    if (index.state() == KeyIndex.State.UNLOADED) {
      // a column family that is only read by key, never scanned, would otherwise never load
      loadKeyIndex(index);
    }
    final long version = index.version();
    final var bound = index.bound();
    return index.isStable(version)
        && index.covers(bound, key)
        && !index.keys().contains(key)
        && index.version() == version;
  }

  /**
   * Takes a point-in-time view of the committed state for keys that start with the given prefix: a
   * storage cursor and a copy of the matching dirty entries, taken together.
   */
  View<K, V> openView(final KeyRange<K> range) {
    ensureUsable();
    final var from = range.from();
    final var to = range.to();
    final long stamp = publishLock.readLock();
    try {
      final var storageCursor = backend.newCursor();
      metrics.iteratorsOpened.increment();
      metrics.of(backend.groupOf(from)).iterators.increment();
      final var dirtyRange = to == null ? dirty.tailMap(from) : dirty.subMap(from, to);
      return new View<>(storageCursor, new TreeMap<>(dirtyRange), lastSeq);
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /**
   * Call it before a scan of a column family. Loads its key index if it has one that isn't loaded
   * yet, and extends a windowed index that has drained. It costs one map lookup when there is
   * nothing to do.
   */
  void prepareKeyIndex(final long columnFamily) {
    final var index = keyIndexes.get(columnFamily);
    if (index == null) {
      return;
    }
    if (index.state() == KeyIndex.State.UNLOADED) {
      loadKeyIndex(index);
    } else if (index.needsRefill(config.keyIndexMaxKeys())) {
      refillKeyIndex(index);
    }
  }

  private void loadKeyIndex(final KeyIndex<K> index) {
    final long group = index.group();
    final int maxKeys = config.keyIndexMaxKeys();
    final boolean keepLowest = index.policy() != KeyIndexPolicy.KEEP_HIGHEST;
    final var loaded = new ArrayList<K>();
    K next = null;
    final var view = openView(backend.groupRange(group));
    try (final var source = new ViewSource<>(backend, view)) {
      seekToCoveredEnd(source, group, keepLowest);
      next = collect(source, group, maxKeys, loaded);
    }

    final long stamp = publishLock.writeLock();
    try {
      if (index.state() != KeyIndex.State.UNLOADED) {
        return;
      }
      if (next != null && index.policy() == KeyIndexPolicy.DISABLE) {
        disableKeyIndex(index, "it has more than %d keys".formatted(maxKeys));
      } else if (index.lastCommitSeq() > view.viewSeq()) {
        metrics.keyIndexDiscardedLoads.increment();
        if (index.recordDiscardedLoad() >= MAX_DISCARDED_LOADS) {
          disableKeyIndex(index, "commits made %d loads obsolete".formatted(MAX_DISCARDED_LOADS));
        }
      } else {
        index.install(loaded, next);
        metrics.keyIndexLoads.increment();
      }
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * Extends the window of a drained index by a chunk of keys beyond the bound, on the calling
   * thread. A chunk is small, so a scan isn't held up for long.
   */
  private void refillKeyIndex(final KeyIndex<K> index) {
    final var bound = index.bound();
    if (bound == null) {
      return;
    }
    final long group = index.group();
    // away from the covered end: forward for a window of the highest keys, else backward
    final boolean forward = index.policy().keepsLowest();
    final var more = new ArrayList<K>();
    K next = null;
    final var view = openView(backend.groupRange(group));
    try (final var source = new ViewSource<>(backend, view)) {
      if (forward) {
        source.seek(bound);
      } else {
        source.seekForPrev(bound);
      }
      next = collect(source, group, config.keyIndexMaxKeys() / 4, more);
    }

    final long stamp = publishLock.writeLock();
    try {
      if (index.state() != KeyIndex.State.LOADED || !bound.equals(index.bound())) {
        // another thread extended or disabled it first
        return;
      }
      if (index.lastCommitSeq() > view.viewSeq()) {
        metrics.keyIndexDiscardedRefills.increment();
        if (index.recordDiscardedLoad() >= MAX_DISCARDED_LOADS) {
          disableKeyIndex(index, "commits made %d refills obsolete".formatted(MAX_DISCARDED_LOADS));
        }
      } else {
        index.extend(more, next);
        metrics.keyIndexRefills.increment();
      }
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /** Positions the source on the first key of the group at the covered end. */
  private void seekToCoveredEnd(
      final ViewSource<K, V> source, final long group, final boolean keepLowest) {
    if (keepLowest) {
      source.seek(backend.groupRange(group).from());
    } else {
      source.seekForPrev(backend.groupLastSeekKey(group));
    }
  }

  /**
   * Collects up to {@code max} keys of the group from the source's position in its direction.
   * Returns the first key it didn't collect, or null if the group ended first.
   */
  private @Nullable K collect(
      final ViewSource<K, V> source, final long group, final int max, final List<K> into) {
    while (source.isValid() && backend.groupOf(source.key()) == group) {
      if (into.size() >= max) {
        return source.key();
      }
      into.add(source.key());
      source.advance();
    }
    return null;
  }

  /** The key index of a column family, or null if it has none. */
  @Nullable KeyIndex<K> keyIndex(final long columnFamily) {
    return keyIndexes.get(columnFamily);
  }

  /** Registers the counters per column family as gauges in the registry. */
  public void exportMetrics(final io.micrometer.core.instrument.MeterRegistry registry) {
    metrics.export(registry);
  }

  Backend<K, V> backend() {
    return backend;
  }

  WriteBehindMetrics metrics() {
    return metrics;
  }

  /** Under the write lock: applies a commit's keys to the indexes of the column families. */
  private void updateKeyIndexes(final K[] keys, final @Nullable V[] values, final long seq) {
    if (keyIndexes.isEmpty()) {
      return;
    }
    // the keys are sorted, so the keys of a column family are consecutive
    int from = 0;
    while (from < keys.length) {
      final long columnFamily = backend.groupOf(keys[from]);
      int to = from + 1;
      while (to < keys.length && backend.groupOf(keys[to]) == columnFamily) {
        to++;
      }
      final var index = keyIndexes.get(columnFamily);
      if (index != null) {
        index.recordCommit(seq);
        if (index.state() == KeyIndex.State.LOADED) {
          index.apply(keys, values, from, to, config.keyIndexMaxKeys());
          if (index.size() > config.keyIndexMaxKeys()) {
            disableKeyIndex(index, "it has more than %d keys".formatted(config.keyIndexMaxKeys()));
          }
        }
      }
      from = to;
    }
  }

  private void disableKeyIndex(final KeyIndex<K> index, final String reason) {
    index.disable();
    metrics.keyIndexDisables.increment();
    LOG.info(
        "Disabled the key index of column family {} until the next restart, because {}",
        index.group(),
        reason);
  }

  /**
   * Waits until the storage holds every transaction committed before this call. Fails if the writer
   * fails or the flush takes longer than the configured timeout.
   */
  public void awaitFlushed() {
    flusher.awaitFlushed(committedSeq);
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

  /** Called by the writer after the storage holds these versions. */
  private void removeFlushed(final List<Map.Entry<K, Version<V>>> flushed) {
    final long stamp = publishLock.writeLock();
    try {
      for (final var entry : flushed) {
        dirty.remove(entry.getKey(), entry.getValue());
      }
    } finally {
      publishLock.unlockWrite(stamp);
    }
  }

  /**
   * A storage iterator plus the dirty entries of the same instant, restricted to a prefix.
   *
   * @param viewSeq the sequence of the last commit that the view includes
   */
  record View<K, V>(
      Backend.Cursor<K, V> storageCursor, NavigableMap<K, Version<V>> dirty, long viewSeq) {}
}
