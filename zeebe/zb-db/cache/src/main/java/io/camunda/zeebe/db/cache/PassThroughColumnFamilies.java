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
 * The backend column families of one backend {@link TransactionContext}, created on first use. They
 * use {@link RawKey} and {@link RawValue}, so the backend stores the cache's bytes unchanged.
 *
 * <p>Like the context, an instance must only be used by one thread at a time.
 */
final class PassThroughColumnFamilies<
    ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue> {

  private final ZeebeDb<ColumnFamilyNames> backend;
  private final TransactionContext context;
  private final Map<EnumValue, PassThrough> columnFamilies = new HashMap<>();

  PassThroughColumnFamilies(
      final ZeebeDb<ColumnFamilyNames> backend, final TransactionContext context) {
    this.backend = backend;
    this.context = context;
  }

  TransactionContext context() {
    return context;
  }

  /** Returns the backend's value of {@code key}, or {@code null} if the backend doesn't have it. */
  byte @Nullable [] get(final CacheKey key) {
    final var passThrough = columnFamily(key.columnFamily());
    passThrough.key.set(key.key());
    final var value = passThrough.columnFamily.get(passThrough.key);
    return value == null ? null : value.bytes();
  }

  void upsert(final CacheKey key, final byte[] value) {
    final var passThrough = columnFamily(key.columnFamily());
    passThrough.key.set(key.key());
    passThrough.value.set(value);
    passThrough.columnFamily.upsert(passThrough.key, passThrough.value);
  }

  void deleteIfExists(final CacheKey key) {
    final var passThrough = columnFamily(key.columnFamily());
    passThrough.key.set(key.key());
    passThrough.columnFamily.deleteIfExists(passThrough.key);
  }

  /**
   * Scans the backend from {@code startAt}: forward within {@code prefix}, or in reverse over the
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
    final var backendColumnFamily = columnFamily(columnFamily).columnFamily;
    final var start = new RawKey();
    start.set(startAt);
    if (reverse) {
      if (keysOnly) {
        backendColumnFamily.whileTrueReverse(start, key -> visitor.visit(key.bytes(), null));
      } else {
        backendColumnFamily.whileTrueReverse(
            start, (key, value) -> visitor.visit(key.bytes(), value.bytes()));
      }
    } else {
      final var prefixKey = new RawKey();
      prefixKey.set(prefix);
      if (keysOnly) {
        backendColumnFamily.whileEqualPrefix(
            prefixKey, start, key -> visitor.visit(key.bytes(), null));
      } else {
        backendColumnFamily.whileEqualPrefix(
            prefixKey, start, (key, value) -> visitor.visit(key.bytes(), value.bytes()));
      }
    }
  }

  private PassThrough columnFamily(final EnumValue columnFamily) {
    return columnFamilies.computeIfAbsent(columnFamily, this::createColumnFamily);
  }

  @SuppressWarnings("unchecked") // every CacheKey of this DB holds one of its column families
  private PassThrough createColumnFamily(final EnumValue columnFamily) {
    final var key = new RawKey();
    final var value = new RawValue();
    return new PassThrough(
        backend.createColumnFamily((ColumnFamilyNames) columnFamily, context, key, value),
        key,
        value);
  }

  private record PassThrough(
      ColumnFamily<RawKey, RawValue> columnFamily, RawKey key, RawValue value) {}

  /** Receives the entries of a scan: copies the caller may keep. */
  @FunctionalInterface
  interface ScanVisitor {
    boolean visit(byte[] key, byte @Nullable [] value);
  }
}
