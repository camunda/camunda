/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The storage's column families for one of its {@link TransactionContext}s, created on first use.
 * They use {@link RawKey} and {@link RawValue}, so the storage keeps the cache's bytes unchanged.
 *
 * <p>Like the context, an instance must only be used by one thread at a time.
 */
final class StorageColumnFamilies<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue> {

  private final ZeebeDb<ColumnFamilyNames> storage;
  private final TransactionContext context;
  private final Map<EnumValue, StorageColumnFamily> columnFamilies = new HashMap<>();

  StorageColumnFamilies(
      final ZeebeDb<ColumnFamilyNames> storage, final TransactionContext context) {
    this.storage = storage;
    this.context = context;
  }

  TransactionContext context() {
    return context;
  }

  /** Returns the storage's value of {@code key}, or {@code null} if the storage doesn't have it. */
  byte @Nullable [] get(final CacheKey key) {
    final var storageColumnFamily = columnFamily(key.columnFamily());
    storageColumnFamily.key.set(key.key());
    final var value = storageColumnFamily.columnFamily.get(storageColumnFamily.key);
    return value == null ? null : value.bytes();
  }

  void upsert(final CacheKey key, final byte[] value) {
    final var storageColumnFamily = columnFamily(key.columnFamily());
    storageColumnFamily.key.set(key.key());
    storageColumnFamily.value.set(value);
    storageColumnFamily.columnFamily.upsert(storageColumnFamily.key, storageColumnFamily.value);
  }

  void deleteIfExists(final CacheKey key) {
    final var storageColumnFamily = columnFamily(key.columnFamily());
    storageColumnFamily.key.set(key.key());
    storageColumnFamily.columnFamily.deleteIfExists(storageColumnFamily.key);
  }

  /**
   * Scans the storage from {@code startAt}: forward within {@code prefix}, or in reverse over the
   * whole column family. A key-only scan doesn't read values and passes {@code null} for them.
   * Stops when {@code visitor} returns {@code false}.
   */
  void scan(
      final EnumValue columnFamily,
      final byte[] prefix,
      final byte[] startAt,
      final boolean reverse,
      final boolean keysOnly,
      final ScanVisitor visitor) {
    final var storageColumnFamily = columnFamily(columnFamily).columnFamily;
    final var start = new RawKey();
    start.set(startAt);
    if (reverse) {
      if (keysOnly) {
        storageColumnFamily.whileTrueReverse(start, key -> visitor.visit(key.bytes(), null));
      } else {
        storageColumnFamily.whileTrueReverse(
            start, (key, value) -> visitor.visit(key.bytes(), value.bytes()));
      }
    } else {
      final var prefixKey = new RawKey();
      prefixKey.set(prefix);
      if (keysOnly) {
        storageColumnFamily.whileEqualPrefix(
            prefixKey, start, key -> visitor.visit(key.bytes(), null));
      } else {
        storageColumnFamily.whileEqualPrefix(
            prefixKey, start, (key, value) -> visitor.visit(key.bytes(), value.bytes()));
      }
    }
  }

  private StorageColumnFamily columnFamily(final EnumValue columnFamily) {
    return columnFamilies.computeIfAbsent(columnFamily, this::createColumnFamily);
  }

  @SuppressWarnings("unchecked") // every CacheKey of this DB holds one of its column families
  private StorageColumnFamily createColumnFamily(final EnumValue columnFamily) {
    final var key = new RawKey();
    final var value = new RawValue();
    return new StorageColumnFamily(
        storage.createColumnFamily((ColumnFamilyNames) columnFamily, context, key, value),
        key,
        value);
  }

  private record StorageColumnFamily(
      ColumnFamily<RawKey, RawValue> columnFamily, RawKey key, RawValue value) {}

  /** Receives the entries of a scan: copies the caller may keep. */
  @FunctionalInterface
  interface ScanVisitor {
    boolean visit(byte[] key, byte @Nullable [] value);
  }
}
