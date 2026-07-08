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
 * A {@link KeyValueStore} over a single ZeebeDb column family, split into two independently usable
 * halves: writes go through the provider's write {@link TransactionContext} (each write is its own
 * transaction unless it joins an enclosing {@link
 * io.camunda.eventbridge.streaming.state.api.StateStoreProvider#runInTransaction} scope), while
 * reads and scans go through a second {@link ColumnFamily} handle bound to the provider's dedicated
 * read context.
 *
 * <p><b>Visibility contract:</b> reads observe <em>committed</em> state only. A write sitting in an
 * open (not yet committed) transaction on the write context is invisible to the read path — by
 * design: dirty state is expected to live in heap overlays above this store (e.g. the caching
 * layer), so committed-only visibility is exactly what a cache miss needs.
 *
 * <p><b>Threading contract:</b> because the two halves use separate transaction contexts (separate
 * write batches, separate key/value serialization buffers), one thread may run write transactions
 * while another thread reads and scans concurrently. Each half is itself single-threaded: at most
 * one thread writes at a time and at most one thread reads at a time.
 */
final class RocksDbKeyValueStore<K extends DbKey, V extends DbValue>
    implements KeyValueStore<K, V> {

  private final ColumnFamily<K, V> writeColumnFamily;
  private final TransactionContext writeContext;
  private final ColumnFamily<K, V> readColumnFamily;

  RocksDbKeyValueStore(
      final ColumnFamily<K, V> writeColumnFamily,
      final TransactionContext writeContext,
      final ColumnFamily<K, V> readColumnFamily) {
    this.writeColumnFamily = writeColumnFamily;
    this.writeContext = writeContext;
    this.readColumnFamily = readColumnFamily;
  }

  @Override
  public void put(final K key, final V value) {
    writeContext.runInTransaction(() -> writeColumnFamily.upsert(key, value));
  }

  @Override
  public void delete(final K key) {
    writeContext.runInTransaction(() -> writeColumnFamily.deleteIfExists(key));
  }

  @Override
  public Optional<V> get(final K key) {
    return Optional.ofNullable(readColumnFamily.get(key));
  }

  @Override
  public boolean exists(final K key) {
    return readColumnFamily.exists(key);
  }

  @Override
  public void prefixScan(final DbKey prefix, final BiConsumer<K, V> visitor) {
    readColumnFamily.whileEqualPrefix(prefix, visitor);
  }

  @Override
  public void prefixScanKeys(final DbKey prefix, final Consumer<K> visitor) {
    // The key-only overload skips reading each value from RocksDB entirely.
    readColumnFamily.whileEqualPrefix(prefix, visitor);
  }

  @Override
  public void forEach(final BiConsumer<K, V> visitor) {
    readColumnFamily.forEach(visitor);
  }
}
