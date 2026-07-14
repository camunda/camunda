/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import java.util.Collection;

/**
 * The owner-thread transaction context of the layered path, mirroring the reference
 * implementation's semantics over one reusable {@link LayeredTransaction}: nested {@link
 * #runInTransaction} calls join the open transaction, the outermost call commits on success and
 * rolls back on failure, runtime exceptions from the operation propagate unwrapped, and checked
 * exceptions are wrapped like the reference does.
 */
final class LayeredTransactionContext implements TransactionContext {

  private final LayeredTransaction transaction;

  LayeredTransactionContext(final Collection<LayeredKeyValueStore> stores) {
    transaction = new LayeredTransaction(stores);
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
    if (!transaction.isInCurrentTransaction()) {
      transaction.resetTransaction();
    }
    return transaction;
  }

  /** Whether a transaction is currently open (started but neither committed nor rolled back). */
  boolean transactionOpen() {
    return transaction.isInCurrentTransaction();
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    try {
      transaction.resetTransaction();
      operations.run();
      transaction.commitInternal();
    } finally {
      // a no-op after a successful commit (staging is empty); the whole rollback otherwise
      transaction.rollbackInternal();
    }
  }
}
