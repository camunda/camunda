/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.rocksdb;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link KeyValueStore} over a single ZeebeDb {@link ColumnFamily}. Writes run in a transaction
 * so each is atomic and durable; reads and scans delegate to the column family directly.
 */
final class RocksDbKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V> {

  private final ColumnFamily<K, V> columnFamily;
  private final TransactionContext context;

  RocksDbKeyValueStore(final ColumnFamily<K, V> columnFamily, final TransactionContext context) {
    this.columnFamily = columnFamily;
    this.context = context;
  }

  @Override
  public void put(final K key, final V value) {
    context.runInTransaction(() -> columnFamily.upsert(key, value));
  }

  @Override
  public void delete(final K key) {
    context.runInTransaction(() -> columnFamily.deleteIfExists(key));
  }

  @Override
  public Optional<V> get(final K key) {
    return Optional.ofNullable(columnFamily.get(key));
  }

  @Override
  public boolean exists(final K key) {
    return columnFamily.exists(key);
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
    columnFamily.whileEqualPrefix(prefix, visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
    // The key-only overload skips reading each value from RocksDB entirely.
    columnFamily.whileEqualPrefix(prefix, visitor);
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    columnFamily.forEach(visitor);
  }
}
