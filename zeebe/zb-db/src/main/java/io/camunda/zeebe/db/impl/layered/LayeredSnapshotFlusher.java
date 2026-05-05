/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import org.agrona.concurrent.UnsafeBuffer;

final class LayeredSnapshotFlusher {

  private LayeredSnapshotFlusher() {}

  static void flush(final LayeredZeebeDb<?> db) {
    final var activeDb = (InMemoryZeebeDb<?>) db.activeDb();
    final var persistentDb = (ZeebeTransactionDb<?>) db.persistentDb();
    final var tombstones = db.snapshotCommittedTombstones();
    final var persistentContext = persistentDb.createContext();

    persistentContext.runInTransaction(
        () -> {
          final var transaction = (ZeebeTransaction) persistentContext.getCurrentTransaction();

          activeDb.forEachCommittedEntry(
              (rawKey, value) -> {
                final byte[] bytes = new byte[value.getLength()];
                final var buffer = new UnsafeBuffer(bytes);
                value.write(buffer, 0);
                try {
                  persistentDb.rawPut(transaction, rawKey, bytes);
                } catch (final Exception e) {
                  throw new RuntimeException(e);
                }
              });

          for (final byte[] tombstone : tombstones) {
            persistentDb.rawDelete(transaction, tombstone);
          }
        });

    db.clearCommittedTombstones();
  }
}
