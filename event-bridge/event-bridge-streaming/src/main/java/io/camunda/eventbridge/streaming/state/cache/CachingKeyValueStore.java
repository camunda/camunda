/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.cache;

import io.camunda.eventbridge.streaming.internals.StoreMetrics;
import io.camunda.eventbridge.streaming.state.api.Checkpointable;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A bytes-bounded, read-through write-back cache over a durable {@link KeyValueStore}, layered so
 * checkpoints can persist asynchronously. The overlay lifecycle mirrors RocksDB's active/immutable
 * memtable pair: the active buffer is frozen at a switch point, persisted in the background, then
 * retired. Reads resolve top-down, first hit wins:
 *
 * <ol>
 *   <li><em>Active overlay</em> — dirty writes buffered since the last {@link #freeze()}. Every
 *       {@link #put} and {@link #delete} lands here and only here.
 *   <li><em>Frozen overlay</em> — the immutable snapshot currently being persisted. Never mutated:
 *       a write to a key present here creates a new active-overlay entry instead, because an IO
 *       thread may be reading the frozen entry concurrently.
 *   <li><em>Clean cache</em> — delegate-backed hot entries, access-ordered so the eldest is the
 *       eviction candidate, trimmed to the byte budget.
 *   <li>The durable delegate — a read miss falls through and populates the clean cache.
 * </ol>
 *
 * <p>Checkpointing splits into three steps so processing pauses only for the cheap one: {@link
 * #freeze()} swaps the active overlay into the frozen slot (pointer swaps only), {@link
 * #persistFrozen()} drains the frozen entries to the delegate inside the commit transaction, and
 * {@link #completeFrozen(boolean)} retires them into the clean cache on success or merges them back
 * into the active overlay on failure so the next freeze re-includes them. {@link #checkpoint()}
 * remains the synchronous composition of the three — an inline cut on the owner thread.
 *
 * <p>Overlay entries — active and frozen alike — are pinned: persisting or evicting them outside
 * the checkpoint transaction would put durable state ahead of the committed offset and break replay
 * recovery. The byte budget is therefore a soft bound while overlay entries exist (only clean
 * entries evict) and a firm one after a successful checkpoint retires and trims the working set.
 * Recovery stays changelog-free: the delegate advances as one atomic cut with the offsets, and a
 * crash replays the source log.
 *
 * <p>Reads and scans see the buffered writes: {@link #get}/{@link #exists} serve overlay values and
 * hide tombstones, and {@link #forEach}/{@link #prefixScan} merge the delegate's sorted stream with
 * both sorted overlay indexes in key order — active wins over frozen wins over delegate, and a
 * tombstone at any overlay level hides everything below. Keys and values are serialized bytes in
 * unsigned-byte order, matching the delegate's scan semantics.
 *
 * <p><b>Threading:</b> single-writer with one exception. Every method except {@link
 * #persistFrozen()} must run on the owner thread. {@link #persistFrozen()} may run on an IO thread:
 * it touches only the frozen overlay (immutable by then), its own dedicated flyweights, and the
 * delegate — never the active overlay or the clean cache, which the owner thread may be using
 * concurrently. The caller's executor handoff provides the happens-before edges around freeze and
 * completion; there is no internal locking.
 *
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public final class CachingKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V>, Checkpointable {

  private static final byte[] NO_PREFIX = new byte[0];

  private final KeyValueStore<K, V> delegate;
  // Owner-thread flyweights, used by reads and scans.
  private final K keyFlyweight;
  private final V valueFlyweight;
  // Dedicated flyweights for persistFrozen, which may run on an IO thread while the owner
  // thread keeps wrapping the pair above — sharing them would race.
  private final K persistKeyFlyweight;
  private final V persistValueFlyweight;
  private final long maxBytes;

  // Active overlay: a hash map for point lookups plus a sorted index (unsigned key order) sharing
  // the same CacheEntry objects, so scans read their overlay from a subMap (O(matches), not
  // O(entries)). Hash keys are content-equal ByteBuffers, each wrapping a full, offset-0 byte[].
  // freeze() steals both structures wholesale and installs fresh empty ones.
  private HashMap<ByteBuffer, CacheEntry> activeMap = new HashMap<>();
  private TreeMap<byte[], CacheEntry> activeIndex = new TreeMap<>(Arrays::compareUnsigned);

  // Frozen overlay: non-null iff a snapshot is outstanding (between freeze and completeFrozen).
  // Same shape as the active overlay; never mutated while outstanding.
  private Map<ByteBuffer, CacheEntry> frozenMap;
  private NavigableMap<byte[], CacheEntry> frozenIndex;

  // Clean cache: delegate-backed entries only, access-ordered so the eldest entry is the LRU
  // eviction candidate. Disjoint from both overlays — a key lives in at most one of
  // {active, clean}, plus possibly the frozen overlay (whose entry then shadows the layers below).
  private final LinkedHashMap<ByteBuffer, CacheEntry> cleanCache =
      new LinkedHashMap<>(16, 0.75f, true);

  // Approximate heap footprint of active + frozen + clean together.
  private long approxBytes;

  // Opt-in: a delete of a never-flushed dirty put annihilates the pair in the active overlay —
  // neither the put nor a tombstone ever reaches the delegate. Sound ONLY under the caller's
  // guarantee that a deleted key is never read again (deletes are garbage collection of dead rows,
  // not semantics): if the delegate holds an older flushed value for the key, skipping the
  // tombstone leaves it as unreachable dead space (reclaimed by compaction), never as a
  // resurrectable read. A put whose key sits in the frozen overlay or the delegate is flushed —
  // its delete must reach the delegate as a tombstone.
  private final boolean absorbDeletes;

  public CachingKeyValueStore(
      final KeyValueStore<K, V> delegate,
      final Supplier<K> keyFlyweights,
      final Supplier<V> valueFlyweights,
      final long maxBytes) {
    this(delegate, keyFlyweights, valueFlyweights, maxBytes, false);
  }

  /**
   * @param keyFlyweights must return a fresh flyweight on each call — the owner thread and the
   *     persist step each need their own
   * @param valueFlyweights must return a fresh flyweight on each call, like {@code keyFlyweights}
   */
  public CachingKeyValueStore(
      final KeyValueStore<K, V> delegate,
      final Supplier<K> keyFlyweights,
      final Supplier<V> valueFlyweights,
      final long maxBytes,
      final boolean absorbDeletes) {
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("maxBytes must be > 0, was " + maxBytes);
    }
    this.delegate = delegate;
    keyFlyweight = keyFlyweights.get();
    valueFlyweight = valueFlyweights.get();
    persistKeyFlyweight = keyFlyweights.get();
    persistValueFlyweight = valueFlyweights.get();
    this.maxBytes = maxBytes;
    this.absorbDeletes = absorbDeletes;
  }

  @Override
  public void put(final K key, final V value) {
    final byte[] keyBytes = toBytes(key);
    final byte[] valueBytes = toBytes(value);
    final CacheEntry entry = activeMap.get(ByteBuffer.wrap(keyBytes));
    if (entry != null) {
      // In-place update of an active-overlay entry; flushed is a property of the key's presence in
      // the delegate, so it carries over.
      approxBytes += valueBytes.length - footprintValue(entry);
      entry.value = valueBytes;
      entry.tombstone = false;
    } else {
      final CacheEntry created = new CacheEntry(valueBytes, false, shadowsFlushedKey(keyBytes));
      activeMap.put(ByteBuffer.wrap(keyBytes), created);
      activeIndex.put(keyBytes, created);
      approxBytes += keyBytes.length + valueBytes.length;
    }
    evictIfNeeded();
  }

  @Override
  public void delete(final K key) {
    final byte[] keyBytes = toBytes(key);
    final CacheEntry entry = activeMap.get(ByteBuffer.wrap(keyBytes));
    if (entry != null) {
      if (absorbDeletes && !entry.tombstone && !entry.flushed) {
        // The put never reached the delegate (and no frozen put is headed there) — the pair
        // annihilates: no write, no tombstone.
        activeMap.remove(ByteBuffer.wrap(keyBytes));
        activeIndex.remove(keyBytes);
        approxBytes -= keyBytes.length + footprintValue(entry);
      } else {
        approxBytes -= footprintValue(entry);
        entry.value = null;
        entry.tombstone = true;
      }
    } else {
      final CacheEntry created = new CacheEntry(null, true, shadowsFlushedKey(keyBytes));
      activeMap.put(ByteBuffer.wrap(keyBytes), created);
      activeIndex.put(keyBytes, created);
      approxBytes += keyBytes.length;
    }
    // A tombstone is pinned in the active overlay; it must outlive eviction so a read-through
    // does not resurrect the delegate's value before the delete is checkpointed.
  }

  /**
   * Whether a new active-overlay entry for {@code keyBytes} starts out flushed: the delegate
   * already holds the key (it shadows a clean entry) or is about to (it shadows a frozen put headed
   * there). Evicts the shadowed clean entry — the overlay would hide it anyway, and dropping it
   * keeps the clean cache disjoint from the overlays.
   */
  private boolean shadowsFlushedKey(final byte[] keyBytes) {
    final CacheEntry clean = cleanCache.remove(ByteBuffer.wrap(keyBytes));
    if (clean != null) {
      approxBytes -= keyBytes.length + clean.value.length;
      return true;
    }
    if (frozenMap != null) {
      final CacheEntry frozen = frozenMap.get(ByteBuffer.wrap(keyBytes));
      // A frozen tombstone is headed to the delegate as a delete, so the key stays un-flushed.
      return frozen != null && !frozen.tombstone;
    }
    return false;
  }

  @Override
  public Optional<V> get(final K key) {
    final byte[] keyBytes = toBytes(key);
    final CacheEntry overlay = overlayEntry(keyBytes);
    if (overlay != null) {
      if (overlay.tombstone) {
        return Optional.empty();
      }
      wrap(valueFlyweight, overlay.value);
      return Optional.of(valueFlyweight);
    }
    final CacheEntry clean = cleanCache.get(ByteBuffer.wrap(keyBytes));
    if (clean != null) {
      wrap(valueFlyweight, clean.value);
      return Optional.of(valueFlyweight);
    }
    final Optional<V> fromDelegate = delegate.get(key);
    if (fromDelegate.isEmpty()) {
      return Optional.empty();
    }
    final byte[] valueBytes = toBytes(fromDelegate.get());
    cleanCache.put(ByteBuffer.wrap(keyBytes), new CacheEntry(valueBytes, false, true));
    approxBytes += keyBytes.length + valueBytes.length;
    evictIfNeeded();
    wrap(valueFlyweight, valueBytes);
    return Optional.of(valueFlyweight);
  }

  @Override
  public boolean exists(final K key) {
    final byte[] keyBytes = toBytes(key);
    final CacheEntry overlay = overlayEntry(keyBytes);
    if (overlay != null) {
      return !overlay.tombstone;
    }
    if (cleanCache.get(ByteBuffer.wrap(keyBytes)) != null) {
      return true;
    }
    return delegate.exists(key);
  }

  /** The overlay entry shadowing {@code keyBytes}, active before frozen; null if neither has it. */
  private CacheEntry overlayEntry(final byte[] keyBytes) {
    final CacheEntry active = activeMap.get(ByteBuffer.wrap(keyBytes));
    if (active != null) {
      return active;
    }
    return frozenMap == null ? null : frozenMap.get(ByteBuffer.wrap(keyBytes));
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
    merge(overlayInPrefix(toBytes(prefix)), sink -> delegate.prefixScan(prefix, sink), visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
    mergeKeys(
        overlayInPrefix(toBytes(prefix)), sink -> delegate.prefixScanKeys(prefix, sink), visitor);
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    merge(overlayInPrefix(NO_PREFIX), delegate::forEach, visitor);
  }

  /**
   * Steals the active overlay into the frozen slot and installs fresh empty structures — pointer
   * swaps only, so processing can resume immediately. The frozen snapshot is immutable from here
   * until {@link #completeFrozen(boolean)} releases it.
   *
   * @throws IllegalStateException if a frozen snapshot is already outstanding — the runtime
   *     guarantees single-flight checkpoints, this guards against a violation
   */
  public void freeze() {
    if (frozenIndex != null) {
      throw new IllegalStateException(
          "cannot freeze: a frozen snapshot is already outstanding and not yet completed");
    }
    frozenMap = activeMap;
    frozenIndex = activeIndex;
    activeMap = new HashMap<>();
    activeIndex = new TreeMap<>(Arrays::compareUnsigned);
  }

  /**
   * Drains the frozen snapshot to the delegate — puts for values, deletes for tombstones. The only
   * method that may run off the owner thread: it reads the immutable frozen overlay through its own
   * flyweights and never touches the active overlay or the clean cache. Failure leaves the frozen
   * snapshot outstanding; the caller decides between retrying and {@link #completeFrozen(boolean)}
   * with {@code success=false}.
   *
   * @throws IllegalStateException if no frozen snapshot is outstanding
   */
  public void persistFrozen() {
    if (frozenIndex == null) {
      throw new IllegalStateException("cannot persist: no frozen snapshot is outstanding");
    }
    for (final Map.Entry<byte[], CacheEntry> frozen : frozenIndex.entrySet()) {
      final CacheEntry entry = frozen.getValue();
      wrap(persistKeyFlyweight, frozen.getKey());
      if (entry.tombstone) {
        delegate.delete(persistKeyFlyweight);
      } else {
        wrap(persistValueFlyweight, entry.value);
        delegate.put(persistKeyFlyweight, persistValueFlyweight);
      }
    }
  }

  /**
   * Releases the frozen snapshot on the owner thread. On success the frozen entries reached the
   * delegate: values retire into the clean cache (delegate-backed, evictable) unless a newer active
   * write shadows them, tombstones drop, and the cache trims back to budget. On failure every
   * frozen entry merges back into the active overlay — except where a newer active write exists for
   * the key, which wins — so the next freeze re-includes it.
   *
   * @throws IllegalStateException if no frozen snapshot is outstanding
   */
  public void completeFrozen(final boolean success) {
    if (frozenIndex == null) {
      throw new IllegalStateException("cannot complete: no frozen snapshot is outstanding");
    }
    if (success) {
      retireFrozen();
    } else {
      mergeBackFrozen();
    }
    frozenMap = null;
    frozenIndex = null;
    if (success) {
      // Everything left is clean or freshly active, so the retired working set can be trimmed.
      evictIfNeeded();
    }
  }

  private void retireFrozen() {
    for (final Map.Entry<byte[], CacheEntry> frozen : frozenIndex.entrySet()) {
      final byte[] keyBytes = frozen.getKey();
      final CacheEntry entry = frozen.getValue();
      if (entry.tombstone) {
        // The delete reached the delegate; a later read-through correctly misses.
        approxBytes -= keyBytes.length;
        continue;
      }
      final CacheEntry shadow = activeMap.get(ByteBuffer.wrap(keyBytes));
      if (shadow != null) {
        // A newer active write supersedes the retired value — drop it rather than cache a stale
        // shadowed copy. The delegate now holds the key, so the shadowing write is flushed: its
        // delete must reach the delegate as a tombstone.
        approxBytes -= keyBytes.length + entry.value.length;
        if (!shadow.tombstone) {
          shadow.flushed = true;
        }
      } else {
        entry.flushed = true; // the delegate now holds this key — a later delete must tombstone
        cleanCache.put(ByteBuffer.wrap(keyBytes), entry);
      }
    }
  }

  private void mergeBackFrozen() {
    for (final Map.Entry<byte[], CacheEntry> frozen : frozenIndex.entrySet()) {
      final byte[] keyBytes = frozen.getKey();
      final CacheEntry entry = frozen.getValue();
      if (activeMap.containsKey(ByteBuffer.wrap(keyBytes))) {
        // The key was re-written after the freeze — the newer active entry wins outright.
        approxBytes -= keyBytes.length + footprintValue(entry);
      } else {
        activeMap.put(ByteBuffer.wrap(keyBytes), entry);
        activeIndex.put(keyBytes, entry);
      }
    }
  }

  /**
   * The synchronous composition of {@link #freeze()}, {@link #persistFrozen()} and {@link
   * #completeFrozen(boolean)} — an inline cut on the owner thread. A persist failure merges the
   * snapshot back before rethrowing, so every entry is dirty again for the retried checkpoint.
   */
  @Override
  public void checkpoint() {
    freeze();
    try {
      persistFrozen();
    } catch (final RuntimeException | Error e) {
      completeFrozen(false);
      throw e;
    }
    completeFrozen(true);
  }

  /**
   * Whether the cache is over its byte budget because overlay (un-flushable) entries could not be
   * evicted. It is a signal to the runtime to run the commit barrier now: checkpointing flushes
   * those entries as one atomic cut with the offset, after which they are clean and evicted.
   * Flushing them any earlier would put durable state ahead of the committed offset.
   */
  public boolean overCapacity() {
    return approxBytes > maxBytes;
  }

  /**
   * Wires the "is state healthy" overlay gauges for {@code storeName}; at most one, and only for a
   * cache that lives as long as its {@code metrics} instance — the bound suppliers capture
   * <em>this</em> cache generation. An owner that rebuilds its caches (live reload) must instead
   * bind suppliers over its own live-state field using {@link #activeOverlayEntries()} and {@link
   * #approximateBytes()}, so the gauge tracks whichever generation is current.
   */
  public void metrics(final StoreMetrics metrics, final String storeName) {
    metrics.bindOverlay(storeName, this::activeOverlayEntries, this::approximateBytes);
  }

  /**
   * The active overlay's entry count — the records cache whose growth forces early cuts.
   * Racy-read-safe for gauges: a stale reading from a scrape thread is harmless.
   */
  public long activeOverlayEntries() {
    return activeMap.size();
  }

  /**
   * The approximate byte footprint of this cache — active, frozen and clean layers combined (they
   * are not tracked separately). Racy-read-safe for gauges.
   */
  public long approximateBytes() {
    return approxBytes;
  }

  private void evictIfNeeded() {
    if (approxBytes <= maxBytes || cleanCache.isEmpty()) {
      return; // under budget, or everything is pinned in an overlay — nothing to evict
    }
    final Iterator<Map.Entry<ByteBuffer, CacheEntry>> it = cleanCache.entrySet().iterator();
    while (approxBytes > maxBytes && it.hasNext()) {
      final Map.Entry<ByteBuffer, CacheEntry> clean = it.next();
      approxBytes -= clean.getKey().remaining() + clean.getValue().value.length;
      it.remove();
    }
  }

  /**
   * The overlay entries whose key starts with {@code prefixBytes}, in unsigned key order, active
   * shadowing frozen on equal keys — a lazy merge of range selections on both sorted indexes, never
   * a walk of the caches.
   */
  private Iterator<Map.Entry<byte[], CacheEntry>> overlayInPrefix(final byte[] prefixBytes) {
    final Iterator<Map.Entry<byte[], CacheEntry>> active =
        inPrefix(activeIndex, prefixBytes).entrySet().iterator();
    if (frozenIndex == null) {
      return active;
    }
    return new OverlayMergeIterator(
        active, inPrefix(frozenIndex, prefixBytes).entrySet().iterator());
  }

  /**
   * The index view holding exactly the entries whose key starts with {@code prefixBytes}: every
   * such key is {@code >= prefixBytes} and {@code < successor(prefixBytes)} in unsigned byte order.
   */
  private static NavigableMap<byte[], CacheEntry> inPrefix(
      final NavigableMap<byte[], CacheEntry> index, final byte[] prefixBytes) {
    if (prefixBytes.length == 0) {
      return index;
    }
    final byte[] upper = successor(prefixBytes);
    return upper == null
        ? index.tailMap(prefixBytes, true)
        : index.subMap(prefixBytes, true, upper, false);
  }

  /**
   * The smallest byte string greater than every string prefixed by {@code prefix}: the prefix with
   * its last non-0xFF byte incremented (and the tail dropped). {@code null} for an all-0xFF prefix,
   * which has no upper bound.
   */
  private static byte[] successor(final byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final byte[] upper = Arrays.copyOf(prefix, i + 1);
        upper[i]++;
        return upper;
      }
    }
    return null;
  }

  /**
   * Merges the delegate's entries with the overlay stream in key order: an overlay put is emitted
   * in place (overriding the delegate's entry for the same key), a tombstone hides the delegate's
   * entry, and unchanged delegate entries pass through.
   */
  private void merge(
      final Iterator<Map.Entry<byte[], CacheEntry>> overlay,
      final Consumer<BiConsumer<K, V>> delegateScan,
      final BiConsumer<K, V> visitor) {
    // A one-slot cursor so the delegate callback can advance the overlay stream as it goes.
    @SuppressWarnings("unchecked")
    final Map.Entry<byte[], CacheEntry>[] pending =
        new Map.Entry[] {overlay.hasNext() ? overlay.next() : null};
    delegateScan.accept(
        (delegateKey, delegateValue) -> {
          final byte[] keyBytes = toBytes(delegateKey);
          // Overlay keys strictly before the delegate's: emit the puts; a tombstone here shadows a
          // key the delegate has already passed (or never had), so it just drops out.
          while (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) < 0) {
            emit(pending[0], visitor);
            pending[0] = overlay.hasNext() ? overlay.next() : null;
          }
          if (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) == 0) {
            emit(pending[0], visitor); // an overlay put overrides, a tombstone hides
            pending[0] = overlay.hasNext() ? overlay.next() : null;
          } else {
            visitor.accept(delegateKey, delegateValue); // unchanged delegate entry
          }
        });
    while (pending[0] != null) { // overlay keys after the last delegate key
      emit(pending[0], visitor);
      pending[0] = overlay.hasNext() ? overlay.next() : null;
    }
  }

  private void emit(final Map.Entry<byte[], CacheEntry> overlay, final BiConsumer<K, V> visitor) {
    final CacheEntry entry = overlay.getValue();
    if (entry.tombstone) {
      return;
    }
    wrap(keyFlyweight, overlay.getKey());
    wrap(valueFlyweight, entry.value);
    visitor.accept(keyFlyweight, valueFlyweight);
  }

  /**
   * The key-only counterpart of {@link #merge}: interleaves overlay put keys with the delegate's
   * keys in key order, dropping tombstoned keys, without touching any value.
   */
  private void mergeKeys(
      final Iterator<Map.Entry<byte[], CacheEntry>> overlay,
      final Consumer<Consumer<K>> delegateScan,
      final Consumer<K> visitor) {
    @SuppressWarnings("unchecked")
    final Map.Entry<byte[], CacheEntry>[] pending =
        new Map.Entry[] {overlay.hasNext() ? overlay.next() : null};
    delegateScan.accept(
        delegateKey -> {
          final byte[] keyBytes = toBytes(delegateKey);
          while (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) < 0) {
            emitKey(pending[0], visitor);
            pending[0] = overlay.hasNext() ? overlay.next() : null;
          }
          if (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) == 0) {
            emitKey(pending[0], visitor); // an overlay put shadows, a tombstone hides
            pending[0] = overlay.hasNext() ? overlay.next() : null;
          } else {
            visitor.accept(delegateKey); // unchanged delegate key
          }
        });
    while (pending[0] != null) { // overlay keys after the last delegate key
      emitKey(pending[0], visitor);
      pending[0] = overlay.hasNext() ? overlay.next() : null;
    }
  }

  private void emitKey(final Map.Entry<byte[], CacheEntry> overlay, final Consumer<K> visitor) {
    if (overlay.getValue().tombstone) {
      return;
    }
    wrap(keyFlyweight, overlay.getKey());
    visitor.accept(keyFlyweight);
  }

  private static long footprintValue(final CacheEntry entry) {
    return entry.value == null ? 0 : entry.value.length;
  }

  private static byte[] toBytes(final BufferWriter writer) {
    final byte[] bytes = new byte[writer.getLength()];
    writer.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }

  private static void wrap(final BufferReader reader, final byte[] bytes) {
    reader.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
  }

  /**
   * A cached entry: a present value or a tombstone. Which layer it lives in — active overlay,
   * frozen overlay, or clean cache — determines whether it is dirty, being persisted, or evictable.
   */
  private static final class CacheEntry {

    private byte[] value; // null iff tombstone
    private boolean tombstone;
    // Whether the delegate holds this key — or an outstanding frozen put is about to make it hold
    // it — so a delete must reach the delegate as a tombstone. A put created blind stays false
    // until the snapshot containing it retires: the delete-absorption window.
    private boolean flushed;

    CacheEntry(final byte[] value, final boolean tombstone, final boolean flushed) {
      this.value = value;
      this.tombstone = tombstone;
      this.flushed = flushed;
    }
  }

  /**
   * Lazily merges the two sorted overlay ranges into one key-ordered stream. On equal keys the
   * active entry wins and the frozen one is skipped, so downstream merging sees at most one overlay
   * entry per key.
   */
  private static final class OverlayMergeIterator
      implements Iterator<Map.Entry<byte[], CacheEntry>> {

    private final Iterator<Map.Entry<byte[], CacheEntry>> active;
    private final Iterator<Map.Entry<byte[], CacheEntry>> frozen;
    private Map.Entry<byte[], CacheEntry> nextActive;
    private Map.Entry<byte[], CacheEntry> nextFrozen;

    OverlayMergeIterator(
        final Iterator<Map.Entry<byte[], CacheEntry>> active,
        final Iterator<Map.Entry<byte[], CacheEntry>> frozen) {
      this.active = active;
      this.frozen = frozen;
      nextActive = active.hasNext() ? active.next() : null;
      nextFrozen = frozen.hasNext() ? frozen.next() : null;
    }

    @Override
    public boolean hasNext() {
      return nextActive != null || nextFrozen != null;
    }

    @Override
    public Map.Entry<byte[], CacheEntry> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      final int cmp =
          nextActive == null
              ? 1
              : nextFrozen == null
                  ? -1
                  : Arrays.compareUnsigned(nextActive.getKey(), nextFrozen.getKey());
      if (cmp > 0) {
        final Map.Entry<byte[], CacheEntry> emitted = nextFrozen;
        nextFrozen = frozen.hasNext() ? frozen.next() : null;
        return emitted;
      }
      final Map.Entry<byte[], CacheEntry> emitted = nextActive;
      nextActive = active.hasNext() ? active.next() : null;
      if (cmp == 0) {
        nextFrozen = frozen.hasNext() ? frozen.next() : null; // the active write shadows it
      }
      return emitted;
    }
  }
}
