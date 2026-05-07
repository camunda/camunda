/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.NavigableSet;
import org.agrona.concurrent.UnsafeBuffer;

final class LayeredSnapshotFlusher {

  private LayeredSnapshotFlusher() {}

  /**
   * Flushes the in-memory state to RocksDB and returns the set of tombstones that were applied. The
   * caller is responsible for removing these from the committed tombstones.
   *
   * <p>Writes go through {@link RawTransactionalColumnFamily} within a proper RocksDB transaction —
   * not via bare rawPut on the DB handle — so that the column family context is respected and the
   * writes participate in the transaction's commit/rollback lifecycle.
   */
  @SuppressWarnings("unchecked")
  static NavigableSet<byte[]> flush(final LayeredZeebeDb<?> db) {
    final var persistentDb = (ZeebeTransactionDb<ZbColumnFamilies>) db.persistentDb();

    // Capture committed entries AND tombstones atomically under the LayeredZeebeDb monitor.
    // This is the same monitor held by commitActiveAndTombstones(), so we are guaranteed to
    // see a consistent pair: every entry whose tombstone was cleared will be present, and
    // every tombstone corresponds to an entry that is genuinely deleted.
    final var snapshot = db.captureFlushSnapshot();

    // Use a dedicated RawTransactionalColumnFamily for writing. The rawPut/rawDelete methods
    // go through the transaction (not around it), so the writes are properly atomic.
    final var rawCf = new RawTransactionalColumnFamily(persistentDb, ZbColumnFamilies.DEFAULT);

    // Write to RocksDB in a separate transaction — the engine's persistent transaction is
    // not committed at this point and must not be reused.
    final var persistentContext = persistentDb.createContext();

    persistentContext.runInTransaction(
        () -> {
          final var transaction = (ZeebeTransaction) persistentContext.getCurrentTransaction();

          for (final var entry : snapshot.entries()) {
            final var value = entry.getValue();
            final byte[] valueBytes = new byte[value.getLength()];
            final var buffer = new UnsafeBuffer(valueBytes);
            value.write(buffer, 0);
            final var rawKey = entry.getKey();
            try {
              rawCf.rawPut(transaction, rawKey, rawKey.length, valueBytes, valueBytes.length);
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          }

          for (final byte[] tombstone : snapshot.tombstones()) {
            try {
              rawCf.rawDelete(transaction, tombstone, tombstone.length);
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          }
        });

    return snapshot.tombstones();
  }
}
