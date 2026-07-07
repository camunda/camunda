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
import java.util.Arrays;
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
 * A write-back cache over a durable {@link KeyValueStore}: the whole store is held on the heap (the
 * authoritative working copy, loaded from the delegate on construction) and reads/writes never
 * touch the delegate. {@link #checkpoint()} flushes the keys changed and deleted since the last
 * checkpoint to the delegate in one pass — so, wrapped in the driver's checkpoint transaction, the
 * base projection's writes coalesce into the same atomic cut as the aggregations instead of writing
 * through per record (a write-back record-cache model, applied to a plain key-value store).
 *
 * <p>Keys and values are stored as their serialized bytes in unsigned-byte order, matching the
 * RocksDB delegate's {@code forEach}/{@code prefixScan} semantics. Not thread-safe (single-writer,
 * like the rest of the pipeline).
 *
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public final class WriteBackKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V>, Checkpointable {

  private final KeyValueStore<K, V> delegate;
  private final K keyFlyweight;
  private final V valueFlyweight;

  private final NavigableMap<byte[], byte[]> entries = new TreeMap<>(Arrays::compareUnsigned);
  private final NavigableSet<byte[]> dirty = new TreeSet<>(Arrays::compareUnsigned);
  private final NavigableSet<byte[]> deleted = new TreeSet<>(Arrays::compareUnsigned);

  public WriteBackKeyValueStore(
      final KeyValueStore<K, V> delegate, final K keyFlyweight, final V valueFlyweight) {
    this.delegate = delegate;
    this.keyFlyweight = keyFlyweight;
    this.valueFlyweight = valueFlyweight;
    delegate.forEach((key, value) -> entries.put(toBytes(key), toBytes(value)));
  }

  @Override
  public void put(final K key, final V value) {
    final byte[] keyBytes = toBytes(key);
    entries.put(keyBytes, toBytes(value));
    dirty.add(keyBytes);
    deleted.remove(keyBytes);
  }

  @Override
  public void delete(final K key) {
    final byte[] keyBytes = toBytes(key);
    entries.remove(keyBytes);
    dirty.remove(keyBytes);
    deleted.add(keyBytes);
  }

  @Override
  public Optional<V> get(final K key) {
    final byte[] value = entries.get(toBytes(key));
    if (value == null) {
      return Optional.empty();
    }
    wrap(valueFlyweight, value);
    return Optional.of(valueFlyweight);
  }

  @Override
  public boolean exists(final K key) {
    return entries.containsKey(toBytes(key));
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
    final byte[] prefixBytes = toBytes(prefix);
    for (final Map.Entry<byte[], byte[]> entry : entries.tailMap(prefixBytes).entrySet()) {
      if (!startsWith(entry.getKey(), prefixBytes)) {
        break;
      }
      visit(entry, visitor);
    }
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
    final byte[] prefixBytes = toBytes(prefix);
    for (final byte[] keyBytes : entries.tailMap(prefixBytes).keySet()) {
      if (!startsWith(keyBytes, prefixBytes)) {
        break;
      }
      wrap(keyFlyweight, keyBytes);
      visitor.accept(keyFlyweight);
    }
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    for (final Map.Entry<byte[], byte[]> entry : entries.entrySet()) {
      visit(entry, visitor);
    }
  }

  /**
   * Writes the keys changed since the last checkpoint to the delegate and clears the change set.
   */
  @Override
  public void checkpoint() {
    for (final byte[] keyBytes : dirty) {
      wrap(keyFlyweight, keyBytes);
      wrap(valueFlyweight, entries.get(keyBytes));
      delegate.put(keyFlyweight, valueFlyweight);
    }
    for (final byte[] keyBytes : deleted) {
      wrap(keyFlyweight, keyBytes);
      delegate.delete(keyFlyweight);
    }
    dirty.clear();
    deleted.clear();
  }

  private void visit(final Map.Entry<byte[], byte[]> entry, final BiConsumer<K, V> visitor) {
    wrap(keyFlyweight, entry.getKey());
    wrap(valueFlyweight, entry.getValue());
    visitor.accept(keyFlyweight, valueFlyweight);
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
}
