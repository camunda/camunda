/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.SnapshotCopy;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RawTransactionalColumnFamily;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransaction;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBSnapshotCopy implements SnapshotCopy {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDBSnapshotCopy.class);
  private final ZeebeDbFactory<ZbColumnFamilies> factory;

  public RocksDBSnapshotCopy(final ZeebeDbFactory<ZbColumnFamilies> factory) {
    this.factory = factory;
  }

  @Override
  public void withContexts(
      final Path fromPath, final Path toDBPath, final CopyContextConsumer consumer) {
    try (final var toDB =
        (ZeebeTransactionDb<ZbColumnFamilies>) factory.createDb(toDBPath.toFile())) {
      try (final var fromDB =
          (ZeebeTransactionDb<ZbColumnFamilies>) factory.createDb(fromPath.toFile())) {
        final var fromCtx = fromDB.createContext();
        final var toCtx = toDB.createContext();
        consumer.accept(fromDB, fromCtx, toDB, toCtx);
      }
    }
  }

  @Override
  public void copySnapshot(
      final Path fromPath, final Path toDBPath, final Set<ColumnFamilyScope> scopes) {
    withContexts(fromPath, toDBPath, new ScopedCopyContextConsumer(scopes));
  }

  record ScopedCopyContextConsumer(Set<ColumnFamilyScope> scopes) implements CopyContextConsumer {

    @Override
    public void accept(
        final ZeebeTransactionDb<ZbColumnFamilies> fromDB,
        final TransactionContext fromCtx,
        final ZeebeTransactionDb<ZbColumnFamilies> toDB,
        final TransactionContext toCtx) {

      final var abort = new AtomicBoolean(false);
      toCtx.runInTransaction(
          () -> {
            final var toTransaction = (ZeebeTransaction) toCtx.getCurrentTransaction();
            for (final var cf : ZbColumnFamilies.values()) {
              if (!scopes.contains(cf.partitionScope()) || abort.get()) {
                continue;
              }
              LOG.debug("Copying column family '{}'", cf);
              final var fromCf = new RawTransactionalColumnFamily(fromDB, cf, fromCtx);
              final var toCf = new RawTransactionalColumnFamily(toDB, cf, toCtx);
              fromCf.forEach(
                  (key, keyOffset, keyLen, value, valueOffset, valueLen) -> {
                    try {
                      toCf.rawPut(
                          toTransaction, key, keyOffset, keyLen, value, valueOffset, valueLen);
                      return true;
                    } catch (final Exception e) {
                      LOG.error(
                          "Failed to copy column family '{}' on key {} and value with length {} terminating.",
                          cf,
                          new String(key),
                          value.length);
                      LOG.error("Exception", e);
                      abort.set(true);
                      return false;
                    }
                  });
            }
            toTransaction.commit();
          });
    }
  }
}
