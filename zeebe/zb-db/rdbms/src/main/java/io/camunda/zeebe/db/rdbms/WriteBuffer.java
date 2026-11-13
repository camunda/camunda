/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The writes of a transaction that aren't sent to the database yet. The last write of a key wins.
 *
 * <p>Writes are sorted by column family and key, so every transaction locks rows in the same order,
 * which keeps two transactions from waiting on each other.
 */
final class WriteBuffer {

  private final TreeMap<BufferedKey, Write> writes = new TreeMap<>();

  void put(final int columnFamily, final byte[] key, final byte[] value) {
    writes.put(new BufferedKey(columnFamily, key), new Write(value));
  }

  void delete(final int columnFamily, final byte[] key) {
    writes.put(new BufferedKey(columnFamily, key), Write.DELETE);
  }

  /** Returns the last write of the key, or {@code null} if this buffer has none. */
  @Nullable Write get(final int columnFamily, final byte[] key) {
    return writes.get(new BufferedKey(columnFamily, key));
  }

  boolean isEmpty() {
    return writes.isEmpty();
  }

  /**
   * Sends the deletes, then the upserts, each in column family order, so that each kind of write to
   * a table goes in one batch.
   */
  void sendTo(final StateMapper mapper, final int partitionId) {
    for (final Map.Entry<BufferedKey, Write> entry : writes.entrySet()) {
      if (entry.getValue().isDelete()) {
        final var key = entry.getKey();
        final var table = StateTables.of(key.columnFamily());
        mapper.delete(partitionId, table.name(), table.shared(), key.columnFamily(), key.key());
      }
    }
    for (final Map.Entry<BufferedKey, Write> entry : writes.entrySet()) {
      final var value = entry.getValue().value();
      if (value != null) {
        final var key = entry.getKey();
        final var table = StateTables.of(key.columnFamily());
        mapper.upsert(
            partitionId,
            table.name(),
            table.shared(),
            key.columnFamily(),
            key.key(),
            value,
            StateMapper.NO_POSITION);
      }
    }
  }

  void clear() {
    writes.clear();
  }

  /** A new value, or a delete if {@code value} is {@code null}. */
  record Write(byte @Nullable [] value) {

    static final Write DELETE = new Write(null);

    boolean isDelete() {
      return value == null;
    }
  }

  /** Sorts by column family, then by the unsigned bytes of the key, as the database does. */
  private record BufferedKey(int columnFamily, byte[] key) implements Comparable<BufferedKey> {

    @Override
    public int compareTo(final BufferedKey other) {
      final int byColumnFamily = Integer.compare(columnFamily, other.columnFamily);
      return byColumnFamily != 0 ? byColumnFamily : Arrays.compareUnsigned(key, other.key);
    }

    @Override
    public boolean equals(final @Nullable Object other) {
      return other instanceof final BufferedKey that
          && columnFamily == that.columnFamily
          && Arrays.equals(key, that.key);
    }

    @Override
    public int hashCode() {
      return 31 * columnFamily + Arrays.hashCode(key);
    }

    @Override
    public String toString() {
      return "BufferedKey{columnFamily=" + columnFamily + ", key=" + Arrays.toString(key) + '}';
    }
  }
}
