/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.protocol.EnumValue;

/**
 * The write-behind cache's {@link TransactionContext}, one per actor. It follows the same rules as
 * RocksDB's: nested {@link #runInTransaction} calls share one transaction, the outermost call
 * commits, and an error rolls back.
 *
 * <p>It also holds a storage context of its own, through which its column families load what the
 * cache doesn't have.
 */
final class CachedTransactionContext<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements TransactionContext {

  private final CachedTransaction transaction;
  private final StorageColumnFamilies<ColumnFamilyNames> reader;

  CachedTransactionContext(
      final WriteBehindStore<ColumnFamilyNames> store, final ZeebeDb<ColumnFamilyNames> storage) {
    transaction = new CachedTransaction(store);
    reader = new StorageColumnFamilies<>(storage, storage.createContext());
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      if (transaction.isInCurrentTransaction()) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", e);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    return currentTransaction();
  }

  CachedTransaction currentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.resetTransaction();
    }
    return transaction;
  }

  StorageColumnFamilies<ColumnFamilyNames> reader() {
    return reader;
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    try {
      transaction.resetTransaction();
      operations.run();
      transaction.commitInternal();
    } finally {
      transaction.rollbackInternal();
    }
  }
}
