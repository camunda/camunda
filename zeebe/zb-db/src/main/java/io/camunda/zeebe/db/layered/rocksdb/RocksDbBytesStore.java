/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.BytesStore;
import java.util.Arrays;
import java.util.function.BiConsumer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

/**
 * A {@link BytesStore} over one column family. Scans rely on RocksDB's default bytewise comparator,
 * which is exactly the unsigned-byte order the SPI requires — no custom comparator is set.
 */
final class RocksDbBytesStore implements BytesStore {

  private final RocksDB db;
  private final String name;
  private final ColumnFamilyHandle handle;

  RocksDbBytesStore(final RocksDB db, final String name, final ColumnFamilyHandle handle) {
    this.db = db;
    this.name = name;
    this.handle = handle;
  }

  @Override
  public byte[] get(final byte[] key) {
    try {
      return db.get(handle, key);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to read from store '%s'".formatted(name), e);
    }
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    try {
      db.put(handle, key, value);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to write to store '%s'".formatted(name), e);
    }
  }

  @Override
  public void delete(final byte[] key) {
    try {
      db.delete(handle, key);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to delete from store '%s'".formatted(name), e);
    }
  }

  @Override
  public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    prefixScan(db.newIterator(handle), prefix, visitor);
  }

  /**
   * Seeks to the first key at or after {@code prefix} and visits entries until the first key that
   * no longer starts with it — sound because the iterator yields keys in unsigned-byte order, so
   * all prefixed keys form one contiguous range. Takes ownership of (and always closes) the
   * iterator; {@link RocksIterator#key()} and {@link RocksIterator#value()} already return copies.
   */
  static void prefixScan(
      final RocksIterator iterator, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    try (iterator) {
      for (iterator.seek(prefix); iterator.isValid(); iterator.next()) {
        final byte[] key = iterator.key();
        if (!startsWith(key, prefix)) {
          return;
        }
        visitor.accept(key, iterator.value());
      }
    }
  }

  private static boolean startsWith(final byte[] key, final byte[] prefix) {
    return key.length >= prefix.length
        && Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
  }
}
