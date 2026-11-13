/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;

/**
 * The {@link TransactionContext} of the relational storage, one per actor. It follows the same
 * rules as RocksDB's: nested {@link #runInTransaction} calls share one transaction, the outermost
 * call commits, and an error rolls back.
 */
final class RdbmsTransactionContext implements TransactionContext {

  private final RdbmsTransaction transaction;

  RdbmsTransactionContext(final RdbmsTransaction transaction) {
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
    } catch (final Exception e) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", e);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    return currentTransaction();
  }

  RdbmsTransaction currentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.resetTransaction();
    }
    return transaction;
  }

  /**
   * Returns the transaction without starting one, for a point read: it reads in the current
   * transaction if there is one, or else alone.
   */
  RdbmsTransaction transaction() {
    return transaction;
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
