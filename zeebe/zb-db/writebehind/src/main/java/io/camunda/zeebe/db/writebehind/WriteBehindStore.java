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
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Set;
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
 * the storage doesn't have yet, and the read cache holds the latest committed value of the keys
 * that were read or written lately. A commit replaces the cached value of the keys it writes. Only
 * the writer thread encodes values.
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
  // the unflushed commits: commits go to the active batch, the writer seals it to flush it
  private volatile Batches<K, V> batches =
      new Batches<>(new ConcurrentSkipListMap<K, Version<V>>(), null);

  // odd while the writer flushes a sealed batch: a value that a reader loaded from the storage
  // during that time may be older than the one that the writer caches
  private volatile long flushEpoch;
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

  /**
   * The unflushed writes at this instant, without a copy: the latest value per key, or null for a
   * delete. For users that read the storage in other ways than by key, for example with queries,
   * and merge these writes into the result. Take it before such a read: a write that is flushed
   * after it is in the storage when the read starts, and one that is flushed before is in it, so
   * the two together hold every committed write. The writer only drops a flushed batch from the
   * store, the batch itself stays complete for whoever holds it.
   */
  public Unflushed<K, V> unflushed() {
    ensureUsable();
    // the sequence first: a commit in between only makes the snapshot look outdated
    final long seq = committedSeq;
    return new Unflushed<>(seq, batches);
  }

  /**
   * The sequence of the last commit. A snapshot of the unflushed writes holds the commits up to
   * then, and later ones only if they are in the same batch.
   */
  public long committedSeq() {
    return committedSeq;
  }

  /**
   * The unflushed writes as of the commit with the given sequence, kept as the batches that held
   * them. Not a copy: valid until the next commit, see {@link #committedSeq()}.
   */
  public static final class Unflushed<K extends Comparable<K>, V> {
    private final long seq;
    private final Batches<K, V> batches;

    private Unflushed(final long seq, final Batches<K, V> batches) {
      this.seq = seq;
      this.batches = batches;
    }

    public long seq() {
      return seq;
    }

    /** The unflushed writes of the keys from {@code from} to {@code to}, both inclusive. */
    public Map<K, @Nullable V> range(final K from, final K to) {
      return new RangeView<>(
          seq,
          batches.active().subMap(from, true, to, true),
          batches.sealed() == null ? null : batches.sealed().subMap(from, true, to, true));
    }
  }

  /**
   * A sorted view of two batches as of a commit, the active one wins; a delete has a null value.
   * Commits after that one, which may be published to a batch while the view is read, are left out.
   */
  private static final class RangeView<K extends Comparable<K>, V>
      extends AbstractMap<K, @Nullable V> {
    private final long seq;
    private final NavigableMap<K, Version<V>> active;
    private final @Nullable NavigableMap<K, Version<V>> sealed;

    private RangeView(
        final long seq,
        final NavigableMap<K, Version<V>> active,
        final @Nullable NavigableMap<K, Version<V>> sealed) {
      this.seq = seq;
      this.active = active;
      this.sealed = sealed;
    }

    private @Nullable Version<V> versionOf(final Object key) {
      var version = active.get(key);
      version = version == null ? null : version.asOf(seq);
      if (version == null && sealed != null) {
        version = sealed.get(key);
        version = version == null ? null : version.asOf(seq);
      }
      return version;
    }

    @Override
    public boolean isEmpty() {
      return !entrySet().iterator().hasNext();
    }

    @Override
    public boolean containsKey(final Object key) {
      return versionOf(key) != null;
    }

    @Override
    public @Nullable V get(final Object key) {
      final var version = versionOf(key);
      return version == null ? null : version.value();
    }

    @Override
    public Set<Map.Entry<K, @Nullable V>> entrySet() {
      return new AbstractSet<>() {
        @Override
        public Iterator<Map.Entry<K, @Nullable V>> iterator() {
          return new MergedIterator<>(
              visible(active.entrySet().iterator()),
              sealed == null ? Collections.emptyIterator() : visible(sealed.entrySet().iterator()));
        }

        @Override
        public int size() {
          int size = 0;
          for (final var ignored : this) {
            size++;
          }
          return size;
        }
      };
    }

    /** The entries of the iterator as of the commit, without the keys that it didn't write yet. */
    private Iterator<Map.Entry<K, Version<V>>> visible(
        final Iterator<Map.Entry<K, Version<V>>> entries) {
      return new Iterator<>() {
        private Map.@Nullable Entry<K, Version<V>> next = advance();

        private Map.@Nullable Entry<K, Version<V>> advance() {
          while (entries.hasNext()) {
            final var entry = entries.next();
            final var version = entry.getValue().asOf(seq);
            if (version != null) {
              return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), version);
            }
          }
          return null;
        }

        @Override
        public boolean hasNext() {
          return next != null;
        }

        @Override
        public Map.Entry<K, Version<V>> next() {
          final var current = next;
          if (current == null) {
            throw new NoSuchElementException();
          }
          next = advance();
          return current;
        }
      };
    }
  }

  /** Merges two sorted iterators; on equal keys the first one wins. */
  private static final class MergedIterator<K extends Comparable<K>, V>
      implements Iterator<Map.Entry<K, @Nullable V>> {
    private final Iterator<Map.Entry<K, Version<V>>> first;
    private final Iterator<Map.Entry<K, Version<V>>> second;
    private Map.@Nullable Entry<K, Version<V>> nextFirst;
    private Map.@Nullable Entry<K, Version<V>> nextSecond;

    private MergedIterator(
        final Iterator<Map.Entry<K, Version<V>>> first,
        final Iterator<Map.Entry<K, Version<V>>> second) {
      this.first = first;
      this.second = second;
      nextFirst = first.hasNext() ? first.next() : null;
      nextSecond = second.hasNext() ? second.next() : null;
    }

    @Override
    public boolean hasNext() {
      return nextFirst != null || nextSecond != null;
    }

    @Override
    public Map.Entry<K, @Nullable V> next() {
      final var one = nextFirst;
      final var two = nextSecond;
      final Map.Entry<K, Version<V>> entry;
      if (one == null && two == null) {
        throw new NoSuchElementException();
      }
      final int order =
          one == null || two == null
              ? (one == null ? 1 : -1)
              : one.getKey().compareTo(two.getKey());
      if (order <= 0 && one != null) {
        entry = one;
        nextFirst = first.hasNext() ? first.next() : null;
        if (order == 0) {
          nextSecond = second.hasNext() ? second.next() : null;
        }
      } else if (two != null) {
        entry = two;
        nextSecond = second.hasNext() ? second.next() : null;
      } else {
        throw new NoSuchElementException();
      }
      return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue().value());
    }
  }

  /** The unflushed version of a key, or else the cached one; null if the store has neither. */
  @Nullable Version<V> lookupInMemory(final K key) {
    final var unflushed = batches.get(key);
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
    // A batch only leaves the store after the storage holds it and the cache has it, so a key that
    // isn't in a batch has its latest flushed value in the storage. A value that is loaded while
    // the writer flushes may be older than the one it caches, so it isn't cached then.
    final long epoch = flushEpoch;
    final var value = backend.get(key, prototype);
    countStorageGet(key, value != null);
    if (cache != null && (epoch & 1) == 0 && flushEpoch == epoch) {
      cache.put(key, new Version<>(value, 0));
    }
    return value;
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
      // the batches first: what is flushed after this is in the batches we hold, what was flushed
      // before is in the storage that the cursor reads
      final var current = batches;
      final var storageCursor = backend.newCursor();
      metrics.iteratorsOpened.increment();
      metrics.of(backend.groupOf(from)).iterators.increment();
      final var dirtyRange = new TreeMap<K, Version<V>>();
      if (current.sealed() != null) {
        dirtyRange.putAll(
            to == null ? current.sealed().tailMap(from) : current.sealed().subMap(from, to));
      }
      dirtyRange.putAll(
          to == null ? current.active().tailMap(from) : current.active().subMap(from, to));
      return new View<>(storageCursor, dirtyRange, lastSeq);
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
      // without the history that the versions of the batch keep for readers of older commits
      sealed.forEach(
          (key, version) -> cache.put(key, new Version<>(version.value(), version.seq())));
    }
    batches = new Batches<>(batches.active(), null);
    flushEpoch++;
  }

  /** The unflushed commits: the active batch, and the sealed one that the writer is flushing. */
  private record Batches<K extends Comparable<K>, V>(
      NavigableMap<K, Version<V>> active, @Nullable NavigableMap<K, Version<V>> sealed) {

    @Nullable Version<V> get(final K key) {
      final var version = active.get(key);
      return version != null || sealed == null ? version : sealed.get(key);
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
