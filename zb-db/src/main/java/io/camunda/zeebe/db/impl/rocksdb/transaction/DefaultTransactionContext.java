/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.RECOVERABLE_ERROR_CODES;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import org.rocksdb.RocksDBException;
import org.rocksdb.Status;

public final class DefaultTransactionContext implements TransactionContext {

  private final ZeebeTransaction transaction;

  DefaultTransactionContext(final ZeebeTransaction transaction) {
    this.transaction = transaction;
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
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      } else {
        throw new RuntimeException(errorMessage, rdbex);
      }
    } catch (final Exception ex) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", ex);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.resetTransaction();
    }
    return transaction;
  }

  @Override
  public void addListener(final TransactionListener listener) {
    transaction.addListener(listener);
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

  private boolean isRocksDbExceptionRecoverable(final RocksDBException rdbex) {
    final Status status = rdbex.getStatus();
    return RECOVERABLE_ERROR_CODES.contains(status.getCode());
  }
}
