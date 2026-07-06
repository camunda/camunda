/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.cache;

import io.camunda.eventbridge.streaming.state.api.Checkpointable;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A bytes-bounded, read-through write-back cache over a durable {@link KeyValueStore}. Hot entries
 * are held on the heap up to a byte budget; a read miss falls through to the delegate and populates
 * the cache, and writes are buffered and flushed to the delegate only on {@link #checkpoint()}, so
 * they coalesce into the runtime's commit transaction rather than writing through per record.
 *
 * <p>Unlike an unbounded write-back cache, this one evicts under memory pressure — but only
 * <em>clean</em> entries (present in the delegate, so re-readable). A <em>dirty</em> entry (a
 * buffered put or delete not yet checkpointed) is pinned: flushing it early, outside the checkpoint
 * transaction, would put durable state ahead of the committed offset and break replay recovery. The
 * budget is therefore a soft bound between checkpoints — the dirty working set may exceed it — and
 * a firm one afterwards, once checkpoint marks everything clean. Recovery stays changelog-free: the
 * delegate advances as one atomic cut with the offsets, and a crash replays the source log.
 *
 * <p>Reads and scans see the buffered writes: {@link #get}/{@link #exists} serve dirty values and
 * hide tombstones, and {@link #forEach}/{@link #prefixScan} merge the delegate's contents with the
 * dirty overlay in key order. Keys and values are serialized bytes in unsigned-byte order, matching
 * the delegate's scan semantics. Not thread-safe (single-writer, like the rest of the pipeline).
 *
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public final class CachingKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V>, Checkpointable {

  private final KeyValueStore<K, V> delegate;
  private final K keyFlyweight;
  private final V valueFlyweight;
  private final long maxBytes;

  // Access-ordered so the eldest entry is the LRU eviction candidate. Keys are content-equal
  // ByteBuffers (each wraps a full, offset-0 byte[]), so lookups match by key bytes.
  private final LinkedHashMap<ByteBuffer, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
  private long approxBytes;

  public CachingKeyValueStore(
      final KeyValueStore<K, V> delegate,
      final K keyFlyweight,
      final V valueFlyweight,
      final long maxBytes) {
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("maxBytes must be > 0, was " + maxBytes);
    }
    this.delegate = delegate;
    this.keyFlyweight = keyFlyweight;
    this.valueFlyweight = valueFlyweight;
    this.maxBytes = maxBytes;
  }

  @Override
  public void put(final K key, final V value) {
    final byte[] keyBytes = toBytes(key);
    final byte[] valueBytes = toBytes(value);
    final CacheEntry entry = cache.get(ByteBuffer.wrap(keyBytes));
    if (entry == null) {
      cache.put(ByteBuffer.wrap(keyBytes), CacheEntry.dirtyValue(valueBytes));
      approxBytes += keyBytes.length + valueBytes.length;
    } else {
      approxBytes += valueBytes.length - footprintValue(entry);
      entry.value = valueBytes;
      entry.dirty = true;
      entry.tombstone = false;
    }
    evictIfNeeded();
  }

  @Override
  public void delete(final K key) {
    final byte[] keyBytes = toBytes(key);
    final CacheEntry entry = cache.get(ByteBuffer.wrap(keyBytes));
    if (entry == null) {
      cache.put(ByteBuffer.wrap(keyBytes), CacheEntry.tombstone());
      approxBytes += keyBytes.length;
    } else {
      approxBytes -= footprintValue(entry);
      entry.value = null;
      entry.dirty = true;
      entry.tombstone = true;
    }
    // A tombstone is dirty, hence pinned; it must outlive eviction so a read-through does not
    // resurrect the delegate's value before the delete is checkpointed.
  }

  @Override
  public Optional<V> get(final K key) {
    final byte[] keyBytes = toBytes(key);
    final CacheEntry entry = cache.get(ByteBuffer.wrap(keyBytes));
    if (entry != null) {
      if (entry.tombstone) {
        return Optional.empty();
      }
      wrap(valueFlyweight, entry.value);
      return Optional.of(valueFlyweight);
    }
    final Optional<V> fromDelegate = delegate.get(key);
    if (fromDelegate.isEmpty()) {
      return Optional.empty();
    }
    final byte[] valueBytes = toBytes(fromDelegate.get());
    cache.put(ByteBuffer.wrap(keyBytes), CacheEntry.cleanValue(valueBytes));
    approxBytes += keyBytes.length + valueBytes.length;
    evictIfNeeded();
    wrap(valueFlyweight, valueBytes);
    return Optional.of(valueFlyweight);
  }

  @Override
  public boolean exists(final K key) {
    final CacheEntry entry = cache.get(ByteBuffer.wrap(toBytes(key)));
    if (entry != null) {
      return !entry.tombstone;
    }
    return delegate.exists(key);
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
    final byte[] prefixBytes = toBytes(prefix);
    final NavigableMap<byte[], byte[]> dirtyPuts = new TreeMap<>(Arrays::compareUnsigned);
    final NavigableSet<byte[]> tombstones = new TreeSet<>(Arrays::compareUnsigned);
    collectDirty(dirtyPuts, tombstones, prefixBytes);
    merge(dirtyPuts, tombstones, sink -> delegate.prefixScan(prefix, sink), visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
    final byte[] prefixBytes = toBytes(prefix);
    final NavigableMap<byte[], byte[]> dirtyPuts = new TreeMap<>(Arrays::compareUnsigned);
    final NavigableSet<byte[]> tombstones = new TreeSet<>(Arrays::compareUnsigned);
    collectDirty(dirtyPuts, tombstones, prefixBytes);
    mergeKeys(
        dirtyPuts.navigableKeySet(),
        tombstones,
        sink -> delegate.prefixScanKeys(prefix, sink),
        visitor);
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    final NavigableMap<byte[], byte[]> dirtyPuts = new TreeMap<>(Arrays::compareUnsigned);
    final NavigableSet<byte[]> tombstones = new TreeSet<>(Arrays::compareUnsigned);
    collectDirty(dirtyPuts, tombstones, null);
    merge(dirtyPuts, tombstones, delegate::forEach, visitor);
  }

  @Override
  public void checkpoint() {
    for (final Map.Entry<ByteBuffer, CacheEntry> cached : cache.entrySet()) {
      final CacheEntry entry = cached.getValue();
      if (!entry.dirty) {
        continue;
      }
      wrap(keyFlyweight, keyBytes(cached.getKey()));
      if (entry.tombstone) {
        delegate.delete(keyFlyweight);
      } else {
        wrap(valueFlyweight, entry.value);
        delegate.put(keyFlyweight, valueFlyweight);
      }
    }
    // Flushed puts are now clean (delegate-backed, so evictable); flushed tombstones are absent in
    // the delegate, so drop them — a later read-through will correctly miss.
    final Iterator<Map.Entry<ByteBuffer, CacheEntry>> it = cache.entrySet().iterator();
    while (it.hasNext()) {
      final Map.Entry<ByteBuffer, CacheEntry> cached = it.next();
      final CacheEntry entry = cached.getValue();
      if (!entry.dirty) {
        continue;
      }
      if (entry.tombstone) {
        approxBytes -= cached.getKey().remaining();
        it.remove();
      } else {
        entry.dirty = false;
      }
    }
    // Everything is clean now, so the pinned working set can finally be trimmed back to budget.
    evictIfNeeded();
  }

  /**
   * Whether the cache is over its byte budget because dirty (un-flushable) entries could not be
   * evicted. It is a signal to the runtime to run the commit barrier now: {@link #checkpoint()}
   * flushes those entries as one atomic cut with the offset, after which they are clean and
   * evicted. Flushing them any earlier would put durable state ahead of the committed offset.
   */
  public boolean overCapacity() {
    return approxBytes > maxBytes;
  }

  private void evictIfNeeded() {
    if (approxBytes <= maxBytes) {
      return;
    }
    final Iterator<Map.Entry<ByteBuffer, CacheEntry>> it = cache.entrySet().iterator();
    while (approxBytes > maxBytes && it.hasNext()) {
      final Map.Entry<ByteBuffer, CacheEntry> cached = it.next();
      final CacheEntry entry = cached.getValue();
      if (entry.dirty) {
        continue; // pinned until checkpoint (a put or a tombstone)
      }
      approxBytes -= cached.getKey().remaining() + entry.value.length;
      it.remove();
    }
  }

  private void collectDirty(
      final NavigableMap<byte[], byte[]> dirtyPuts,
      final NavigableSet<byte[]> tombstones,
      final byte[] prefixBytes) {
    for (final Map.Entry<ByteBuffer, CacheEntry> cached : cache.entrySet()) {
      final CacheEntry entry = cached.getValue();
      if (!entry.dirty) {
        continue;
      }
      final byte[] keyBytes = keyBytes(cached.getKey());
      if (prefixBytes != null && !startsWith(keyBytes, prefixBytes)) {
        continue;
      }
      if (entry.tombstone) {
        tombstones.add(keyBytes);
      } else {
        dirtyPuts.put(keyBytes, entry.value);
      }
    }
  }

  /**
   * Merges the delegate's entries with the dirty overlay in key order (puts win, tombstones hide).
   */
  private void merge(
      final NavigableMap<byte[], byte[]> dirtyPuts,
      final NavigableSet<byte[]> tombstones,
      final Consumer<BiConsumer<K, V>> delegateScan,
      final BiConsumer<K, V> visitor) {
    final Iterator<Map.Entry<byte[], byte[]>> dirty = dirtyPuts.entrySet().iterator();
    // A one-slot cursor so the delegate callback can advance the dirty stream as it goes.
    @SuppressWarnings("unchecked")
    final Map.Entry<byte[], byte[]>[] pending =
        new Map.Entry[] {dirty.hasNext() ? dirty.next() : null};
    delegateScan.accept(
        (delegateKey, delegateValue) -> {
          final byte[] keyBytes = toBytes(delegateKey);
          while (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) < 0) {
            emit(pending[0].getKey(), pending[0].getValue(), visitor);
            pending[0] = dirty.hasNext() ? dirty.next() : null;
          }
          if (pending[0] != null && Arrays.compareUnsigned(pending[0].getKey(), keyBytes) == 0) {
            emit(pending[0].getKey(), pending[0].getValue(), visitor); // dirty put overrides
            pending[0] = dirty.hasNext() ? dirty.next() : null;
          } else if (!tombstones.contains(keyBytes)) {
            visitor.accept(delegateKey, delegateValue); // unchanged delegate entry
          }
        });
    while (pending[0] != null) { // dirty keys after the last delegate key
      emit(pending[0].getKey(), pending[0].getValue(), visitor);
      pending[0] = dirty.hasNext() ? dirty.next() : null;
    }
  }

  private void emit(
      final byte[] keyBytes, final byte[] valueBytes, final BiConsumer<K, V> visitor) {
    wrap(keyFlyweight, keyBytes);
    wrap(valueFlyweight, valueBytes);
    visitor.accept(keyFlyweight, valueFlyweight);
  }

  /**
   * The key-only counterpart of {@link #merge}: interleaves dirty put keys with the delegate's keys
   * in key order, dropping tombstoned keys, without touching any value.
   */
  private void mergeKeys(
      final NavigableSet<byte[]> dirtyPutKeys,
      final NavigableSet<byte[]> tombstones,
      final Consumer<Consumer<K>> delegateScan,
      final Consumer<K> visitor) {
    final Iterator<byte[]> dirty = dirtyPutKeys.iterator();
    final byte[][] pending = {dirty.hasNext() ? dirty.next() : null};
    delegateScan.accept(
        delegateKey -> {
          final byte[] keyBytes = toBytes(delegateKey);
          while (pending[0] != null && Arrays.compareUnsigned(pending[0], keyBytes) < 0) {
            emitKey(pending[0], visitor);
            pending[0] = dirty.hasNext() ? dirty.next() : null;
          }
          if (pending[0] != null && Arrays.compareUnsigned(pending[0], keyBytes) == 0) {
            emitKey(pending[0], visitor); // a dirty put shadows the delegate's key
            pending[0] = dirty.hasNext() ? dirty.next() : null;
          } else if (!tombstones.contains(keyBytes)) {
            visitor.accept(delegateKey); // unchanged delegate key
          }
        });
    while (pending[0] != null) { // dirty keys after the last delegate key
      emitKey(pending[0], visitor);
      pending[0] = dirty.hasNext() ? dirty.next() : null;
    }
  }

  private void emitKey(final byte[] keyBytes, final Consumer<K> visitor) {
    wrap(keyFlyweight, keyBytes);
    visitor.accept(keyFlyweight);
  }

  private static long footprintValue(final CacheEntry entry) {
    return entry.value == null ? 0 : entry.value.length;
  }

  /** The backing array of a key buffer — always the full, offset-0 array it was created from. */
  private static byte[] keyBytes(final ByteBuffer key) {
    return key.array();
  }

  private static byte[] toBytes(final BufferWriter writer) {
    final byte[] bytes = new byte[writer.getLength()];
    writer.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }

  private static void wrap(final BufferReader reader, final byte[] bytes) {
    reader.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
  }

  private static boolean startsWith(final byte[] candidate, final byte[] prefix) {
    if (candidate.length < prefix.length) {
      return false;
    }
    return Arrays.equals(candidate, 0, prefix.length, prefix, 0, prefix.length);
  }

  /** A cached entry: a present value or a tombstone, either clean (delegate-backed) or dirty. */
  private static final class CacheEntry {

    private byte[] value; // null iff tombstone
    private boolean dirty;
    private boolean tombstone;

    static CacheEntry cleanValue(final byte[] value) {
      final CacheEntry entry = new CacheEntry();
      entry.value = value;
      return entry;
    }

    static CacheEntry dirtyValue(final byte[] value) {
      final CacheEntry entry = new CacheEntry();
      entry.value = value;
      entry.dirty = true;
      return entry;
    }

    static CacheEntry tombstone() {
      final CacheEntry entry = new CacheEntry();
      entry.dirty = true;
      entry.tombstone = true;
      return entry;
    }
  }
}
