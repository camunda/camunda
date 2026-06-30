/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state.memory;

import io.camunda.analytics.state.api.KeyValueStore;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * A heap-backed {@link KeyValueStore} for tests — no RocksDB, no native library. Keys and values
 * are stored as their serialized bytes in a map ordered by unsigned-byte (lexicographic) key order,
 * so {@code forEach} and {@code prefixScan} match the order and prefix semantics of the RocksDB
 * store.
 *
 * <p>It mirrors the flyweight contract: reads wrap the shared key/value flyweights, valid only
 * until the next call.
 */
public final class InMemoryKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V> {

  private final K keyFlyweight;
  private final V valueFlyweight;
  private final NavigableMap<byte[], byte[]> entries = new TreeMap<>(Arrays::compareUnsigned);

  public InMemoryKeyValueStore(final K keyFlyweight, final V valueFlyweight) {
    this.keyFlyweight = keyFlyweight;
    this.valueFlyweight = valueFlyweight;
  }

  @Override
  public void put(final K key, final V value) {
    entries.put(toBytes(key), toBytes(value));
  }

  @Override
  public void delete(final K key) {
    entries.remove(toBytes(key));
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
    // entries with this prefix are contiguous from the first key >= prefix, in key order
    for (final Map.Entry<byte[], byte[]> entry : entries.tailMap(prefixBytes).entrySet()) {
      if (!startsWith(entry.getKey(), prefixBytes)) {
        break;
      }
      visit(entry, visitor);
    }
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    for (final Map.Entry<byte[], byte[]> entry : entries.entrySet()) {
      visit(entry, visitor);
    }
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
