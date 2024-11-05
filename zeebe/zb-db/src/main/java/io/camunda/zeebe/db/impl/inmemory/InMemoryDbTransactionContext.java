/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.util.TreeMap;

class InMemoryDbTransactionContext implements TransactionContext {
  private final InMemoryDbTransaction transaction;

  public InMemoryDbTransactionContext(final TreeMap<Bytes, Bytes> database) {
    transaction = new InMemoryDbTransaction(database);
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      if (transaction.isInCurrentTransaction()) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (final ZeebeDbInconsistentException e) {
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

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    try {
      transaction.resetTransaction();
      operations.run();
      transaction.commit();
    } catch (final Exception e) {
      transaction.rollback();
      throw e;
    }
  }
}
