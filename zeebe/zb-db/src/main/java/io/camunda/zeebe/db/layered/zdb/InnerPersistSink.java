/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.PersistBatch;
import io.camunda.zeebe.db.layered.PersistSink;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link PersistSink} draining persist rounds into the wrapped database: a batch opens the
 * dedicated persist context's transaction, routes every entry through the raw-bytes column family
 * of its store (so the writes land in that transaction's write batch), and {@link
 * PersistBatch#commit()} commits the transaction — one atomic RocksDB write for the whole round.
 *
 * <p><b>Anchor:</b> {@link PersistBatch#putAnchor(long)} is deliberately a no-op and {@link
 * #readAnchor()} always returns -1. In this wiring the engine writes its last-processed position
 * through its own layered column family, so the anchor arrives in the same atomic batch as just
 * another drained key, and recovery reads the position back through the regular state classes — a
 * separate anchor cell would be redundant.
 */
final class InnerPersistSink implements PersistSink {

  private final TransactionContext persistContext;
  private final Map<String, ColumnFamily<DbBytes, DbBytes>> columnFamiliesByStore;

  /**
   * @param columnFamiliesByStore a live view, keyed by store name, over the raw-bytes column
   *     families bound to the persist context
   */
  InnerPersistSink(
      final TransactionContext persistContext,
      final Map<String, ColumnFamily<DbBytes, DbBytes>> columnFamiliesByStore) {
    this.persistContext = Objects.requireNonNull(persistContext, "persistContext");
    this.columnFamiliesByStore =
        Objects.requireNonNull(columnFamiliesByStore, "columnFamiliesByStore");
  }

  @Override
  public PersistBatch newBatch() {
    return new InnerPersistBatch();
  }

  @Override
  public long readAnchor() {
    return -1;
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

  private final class InnerPersistBatch implements PersistBatch {

    private final ZeebeDbTransaction transaction;
    private final DbBytes key = new DbBytes();
    private final DbBytes value = new DbBytes();
    private boolean anchorStaged;
    private boolean committed;

    private InnerPersistBatch() {
      // opens (resets) the persist context's transaction; every put/delete below joins it
      transaction = persistContext.getCurrentTransaction();
    }

    @Override
    public void put(final String storeName, final byte[] keyBytes, final byte[] valueBytes) {
      key.wrapBytes(keyBytes);
      value.wrapBytes(valueBytes);
      columnFamilyOf(storeName).upsert(key, value);
    }

    @Override
    public void delete(final String storeName, final byte[] keyBytes) {
      key.wrapBytes(keyBytes);
      columnFamilyOf(storeName).deleteIfExists(key);
    }

    @Override
    public void putAnchor(final long position) {
      if (anchorStaged) {
        throw new IllegalStateException("anchor already staged for this batch");
      }
      anchorStaged = true;
      // intentionally not persisted — see the class javadoc
    }

    @Override
    public void commit() throws Exception {
      transaction.commit();
      committed = true;
    }

    @Override
    public void close() {
      if (committed) {
        return;
      }
      try {
        transaction.rollback();
      } catch (final Exception e) {
        throw new ZeebeDbException("Failed to roll back an uncommitted persist batch", e);
      }
    }
  }
}
