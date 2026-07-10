/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import io.camunda.zeebe.db.layered.SnapshotSource;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * The fallback {@link SnapshotSource} used when no real one is injected into a {@link
 * LayeredZeebeDb}: its "snapshots" read the latest committed durable state through a dedicated
 * context of the wrapped database, <b>without pinning anything</b>.
 *
 * <p><b>Limitations — read before relying on this:</b> the public {@code ZeebeDb} surface exposes
 * no way to pin a RocksDB snapshot, so a view built on this source is not protected against tearing
 * while a persist round commits concurrently (the phantom/ghost anomalies described in {@link
 * ReadOnlyView}). It is only sound where persist rounds run inline on the owner thread and views
 * are read on that same thread — tests and single-threaded wirings. Deployments with asynchronous
 * view readers or an off-thread persist stage must inject a pinning source (see {@code
 * io.camunda.zeebe.db.layered.rocksdb.RocksDbLayeredBacking#snapshotSource()}). For the same reason
 * the returned snapshots are <b>not</b> safe to read from multiple threads: they share one inner
 * transaction context.
 */
final class UnpinnedSnapshotSource implements SnapshotSource {

  private final Map<String, ColumnFamily<DbBytes, DbBytes>> columnFamiliesByStore;
  private final DbBytes key = new DbBytes();
  private final DbBytes prefix = new DbBytes();

  /**
   * @param columnFamiliesByStore a live view, keyed by store name, over the raw-bytes column
   *     families bound to the dedicated snapshot-read context
   */
  UnpinnedSnapshotSource(final Map<String, ColumnFamily<DbBytes, DbBytes>> columnFamiliesByStore) {
    this.columnFamiliesByStore =
        Objects.requireNonNull(columnFamiliesByStore, "columnFamiliesByStore");
  }

  @Override
  public ReadSnapshot takeSnapshot() {
    return new UnpinnedSnapshot();
  }

  private ColumnFamily<DbBytes, DbBytes> columnFamilyOf(final String storeName) {
    final ColumnFamily<DbBytes, DbBytes> columnFamily = columnFamiliesByStore.get(storeName);
    if (columnFamily == null) {
      throw new IllegalArgumentException(
          "Unknown store '%s'; known stores: %s"
              .formatted(storeName, columnFamiliesByStore.keySet()));
    }
    return columnFamily;
  }

  private final class UnpinnedSnapshot implements ReadSnapshot {

    @Override
    public byte[] get(final String storeName, final byte[] keyBytes) {
      key.wrapBytes(keyBytes);
      final DbBytes value = columnFamilyOf(storeName).get(key);
      return value == null ? null : value.getBytes();
    }

    @Override
    public void prefixScan(
        final String storeName,
        final byte[] prefixBytes,
        final BiConsumer<byte[], byte[]> visitor) {
      prefix.wrapBytes(prefixBytes);
      final BiConsumer<DbBytes, DbBytes> rawVisitor =
          (keyPart, valuePart) -> visitor.accept(keyPart.getBytes(), valuePart.getBytes());
      columnFamilyOf(storeName).whileEqualPrefix(prefix, rawVisitor);
    }

    @Override
    public void close() {
      // nothing pinned, nothing to release
    }
  }
}
