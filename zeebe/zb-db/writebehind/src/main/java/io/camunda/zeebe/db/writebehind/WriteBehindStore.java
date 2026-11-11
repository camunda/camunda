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
import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.RawDbAccess;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
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
 * group from the dirty index, and taking a read view (a storage iterator plus a copy of the dirty
 * index). Nothing that holds it blocks on anything else.
 */
final class WriteBehindStore implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.db.writebehind");
  private static final int MAX_DISCARDED_LOADS = 3;
  private static final int LAST_KEY_SUFFIX_LENGTH = 256;

  private final RawDbAccess storage;
  private final WriteBehindConfiguration config;
  private final ConcurrentSkipListMap<RawKey, Version> dirty = new ConcurrentSkipListMap<>();
  private final @Nullable Cache<RawKey, Version> cache;
  private final StampedLock publishLock = new StampedLock();

  // built in the constructor and never changed, so readers need no lock
  private final Long2ObjectHashMap<KeyIndex> keyIndexes = new Long2ObjectHashMap<>();

  private final AsyncFlusher flusher;

  private final WriteBehindMetrics metrics = new WriteBehindMetrics();

  // guarded by publishLock's write side
  private long lastSeq;

  // written under publishLock's write side, read without it to fail fast
  private volatile boolean closed;

  private volatile long committedSeq;

  WriteBehindStore(final RawDbAccess storage, final WriteBehindConfiguration config) {
    this.storage = storage;
    this.config = config;
    cache =
        config.cacheMaxBytes() > 0
            ? Caffeine.newBuilder()
                .maximumWeight(config.cacheMaxBytes())
                .<RawKey, Version>weigher((key, version) -> version.weigh(key))
                .build()
            : null;
    for (final long columnFamily : config.keyIndexColumnFamilies()) {
      keyIndexes.put(columnFamily, new KeyIndex(columnFamily, config.keyIndexPolicy(columnFamily)));
    }
    flusher = new AsyncFlusher(storage, config, metrics, this::ensureUsable, this::removeFlushed);
    flusher.start();
  }

  /**
   * Publishes a transaction and queues it for the writer. Waits first while too many bytes are
   * unflushed. A transaction above the limit on its own still commits once the queue is empty.
   *
   * @param values the new value per key, or null for a delete; the store takes ownership and nobody
   *     may change them afterwards
   */
  void commit(final RawKey[] keys, final @Nullable DbValue[] values) {
    ensureUsable();
    flusher.awaitCapacity();

    final var versions = new Version[keys.length];
    final long stamp = publishLock.writeLock();
    try {
      ensureUsable();
      final long seq = ++lastSeq;
      long bytes = 0;
      for (int i = 0; i < keys.length; i++) {
        final var version = new Version(values[i], seq);
        versions[i] = version;
        final var counters = metrics.of(keys[i].columnFamily());
        (values[i] == null ? counters.committedDeletes : counters.committedPuts).increment();
        bytes += version.weigh(keys[i]);
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
  @Nullable DbValue get(final RawKey key, final DbValue prototype) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return inMemory.value();
    }
    if (absentFromKeyIndex(key)) {
      metrics.of(key.columnFamily()).keyIndexAbsent.increment();
      return null;
    }
    return readStorage(key, prototype);
  }

  /** The unflushed version of a key, or else the cached one; null if the store has neither. */
  @Nullable Version lookupInMemory(final RawKey key) {
    final var unflushed = dirty.get(key);
    if (unflushed != null) {
      metrics.of(key.columnFamily()).dirtyHits.increment();
      return unflushed;
    }
    final var cached = cache == null ? null : cache.getIfPresent(key);
    if (cached != null) {
      metrics.of(key.columnFamily()).cacheHits.increment();
    }
    return cached;
  }

  /**
   * Reads the latest committed value from the storage, or returns null if there is none, unless a
   * newer version is unflushed. A value read from the storage is decoded into a new instance of
   * {@code prototype}'s class and cached.
   */
  @Nullable DbValue readStorage(final RawKey key, final DbValue prototype) {
    // Under the read lock no commit can publish this key and no flush can remove it, so if it isn't
    // in the dirty index now, the storage has its latest value and nothing can make the cached copy
    // stale before a later commit invalidates it.
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return latest.value();
      }
      final var bytes = storage.get(key.bytes(), key.bytes().length);
      countStorageGet(key, bytes != null);
      final var value = bytes == null ? null : Values.decode(bytes, prototype);
      if (cache != null) {
        cache.put(key, new Version(value, 0));
      }
      return value;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  /** Whether the key has a committed value. Doesn't decode or cache what it reads. */
  boolean exists(final RawKey key) {
    ensureUsable();
    final var inMemory = lookupInMemory(key);
    if (inMemory != null) {
      return !inMemory.isTombstone();
    }
    if (absentFromKeyIndex(key)) {
      metrics.of(key.columnFamily()).keyIndexAbsent.increment();
      return false;
    }
    final long stamp = publishLock.readLock();
    try {
      final var latest = dirty.get(key);
      if (latest != null) {
        return !latest.isTombstone();
      }
      final var found = storage.get(key.bytes(), key.bytes().length) != null;
      countStorageGet(key, found);
      return found;
    } finally {
      publishLock.unlockRead(stamp);
    }
  }

  private void countStorageGet(final RawKey key, final boolean found) {
    final var counters = metrics.of(key.columnFamily());
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
  private boolean absentFromKeyIndex(final RawKey key) {
    if (keyIndexes.isEmpty()) {
      return false;
    }
    final var index = keyIndexes.get(key.columnFamily());
    if (index == null) {
      return false;
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
   * storage iterator and a copy of the matching dirty entries, taken together.
   */
  View openView(final byte[] prefix, final int prefixLength) {
    ensureUsable();
    final var from = RawKey.copyOf(prefix, prefixLength);
    final var to = RawKey.prefixEnd(prefix, prefixLength);
    final long stamp = publishLock.readLock();
    try {
      final var storageIterator = storage.newIterator();
      metrics.iteratorsOpened.increment();
      metrics.of(from.columnFamily()).iterators.increment();
      final var range = to == null ? dirty.tailMap(from) : dirty.subMap(from, to);
      return new View(storageIterator, new TreeMap<>(range), lastSeq);
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

  private void loadKeyIndex(final KeyIndex index) {
    final var prefix = prefixOf(index);
    final int maxKeys = config.keyIndexMaxKeys();
    final boolean keepLowest = index.policy() != KeyIndexPolicy.KEEP_HIGHEST;
    final var loaded = new ArrayList<RawKey>();
    RawKey next = null;
    final var view = openView(prefix, prefix.length);
    try (final var source = new ViewSource(view)) {
      seekToCoveredEnd(source, prefix, keepLowest);
      next = collect(source, prefix, maxKeys, loaded);
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
  private void refillKeyIndex(final KeyIndex index) {
    final var bound = index.bound();
    if (bound == null) {
      return;
    }
    final var prefix = prefixOf(index);
    // away from the covered end: forward for a window of the highest keys, else backward
    final boolean forward = index.policy().keepsLowest();
    final var more = new ArrayList<RawKey>();
    RawKey next = null;
    final var view = openView(prefix, prefix.length);
    try (final var source = new ViewSource(view)) {
      if (forward) {
        source.seek(bound.bytes());
      } else {
        source.seekForPrev(bound.bytes());
      }
      next = collect(source, prefix, config.keyIndexMaxKeys() / 4, more);
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

  private static byte[] prefixOf(final KeyIndex index) {
    final var prefix = new byte[Long.BYTES];
    ByteBuffer.wrap(prefix).order(ZeebeDbConstants.ZB_DB_BYTE_ORDER).putLong(index.columnFamily());
    return prefix;
  }

  /** Positions the source on the first key of the column family at the covered end. */
  private static void seekToCoveredEnd(
      final ViewSource source, final byte[] prefix, final boolean keepLowest) {
    if (keepLowest) {
      source.seek(prefix);
      return;
    }
    // The storage iterators only see keys with the seek key's column family prefix, so the seek
    // key must stay in the column family. No real key has this many 0xFF bytes after the prefix.
    final var last = Arrays.copyOf(prefix, prefix.length + LAST_KEY_SUFFIX_LENGTH);
    Arrays.fill(last, prefix.length, last.length, (byte) 0xFF);
    source.seekForPrev(last);
  }

  /**
   * Collects up to {@code max} keys of the column family from the source's position in its
   * direction. Returns the first key it didn't collect, or null if the column family ended first.
   */
  private static @Nullable RawKey collect(
      final ViewSource source, final byte[] prefix, final int max, final List<RawKey> into) {
    while (source.isValid()
        && Arrays.equals(prefix, 0, prefix.length, source.key(), 0, prefix.length)) {
      if (into.size() >= max) {
        return RawKey.copyOf(source.key());
      }
      into.add(RawKey.copyOf(source.key()));
      source.advance();
    }
    return null;
  }

  /** The key index of a column family, or null if it has none. */
  @Nullable KeyIndex keyIndex(final long columnFamily) {
    return keyIndexes.get(columnFamily);
  }

  /** Registers the counters per column family as gauges in the registry. */
  void exportMetrics(final io.micrometer.core.instrument.MeterRegistry registry) {
    metrics.export(registry);
  }

  WriteBehindMetrics metrics() {
    return metrics;
  }

  /** Under the write lock: applies a commit's keys to the indexes of the column families. */
  private void updateKeyIndexes(
      final RawKey[] keys, final @Nullable DbValue[] values, final long seq) {
    if (keyIndexes.isEmpty()) {
      return;
    }
    // the keys are sorted, so the keys of a column family are consecutive
    int from = 0;
    while (from < keys.length) {
      final long columnFamily = keys[from].columnFamily();
      int to = from + 1;
      while (to < keys.length && keys[to].columnFamily() == columnFamily) {
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

  private void disableKeyIndex(final KeyIndex index, final String reason) {
    index.disable();
    metrics.keyIndexDisables.increment();
    LOG.info(
        "Disabled the key index of column family {} until the next restart, because {}",
        index.columnFamily(),
        reason);
  }

  /**
   * Waits until the storage holds every transaction committed before this call. Fails if the writer
   * fails or the flush takes longer than the configured timeout.
   */
  void awaitFlushed() {
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
  private void removeFlushed(final List<Map.Entry<RawKey, Version>> flushed) {
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
  record View(DbIterator storageIterator, NavigableMap<RawKey, Version> dirty, long viewSeq) {}
}
