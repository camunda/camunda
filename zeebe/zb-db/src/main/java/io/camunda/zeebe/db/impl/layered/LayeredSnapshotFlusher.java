/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import org.agrona.concurrent.UnsafeBuffer;

final class LayeredSnapshotFlusher {

  private LayeredSnapshotFlusher() {}

  static void flush(final LayeredZeebeDb<?> db) {
    final var persistentDb = (ZeebeTransactionDb<?>) db.persistentDb();

    // Capture committed entries AND tombstones atomically under the LayeredZeebeDb monitor.
    // This is the same monitor held by commitActiveAndTombstones(), so we are guaranteed to
    // see a consistent pair: every entry whose tombstone was cleared will be present, and
    // every tombstone corresponds to an entry that is genuinely deleted.
    final var snapshot = db.captureFlushSnapshot();

    // Write to RocksDB in a separate transaction — the engine's persistent transaction is
    // not committed at this point and must not be reused.
    final var persistentContext = persistentDb.createContext();

    persistentContext.runInTransaction(
        () -> {
          final var transaction = (ZeebeTransaction) persistentContext.getCurrentTransaction();

          for (final var entry : snapshot.entries()) {
            final var value = entry.getValue();
            final byte[] bytes = new byte[value.getLength()];
            final var buffer = new UnsafeBuffer(bytes);
            value.write(buffer, 0);
            try {
              persistentDb.rawPut(transaction, entry.getKey(), bytes);
            } catch (final Exception e) {
              throw new RuntimeException(e);
            }
          }

          for (final byte[] tombstone : snapshot.tombstones()) {
            persistentDb.rawDelete(transaction, tombstone);
          }
        });

    // Only remove the tombstones we actually applied — do NOT clear all committed
    // tombstones, as the engine may have added new ones between the capture and now.
    db.removeCommittedTombstones(snapshot.tombstones());
  }
}
