/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;

/**
 * A long-lived handle on the database's single writer connection.
 *
 * <p>Only the outermost {@link #runInTransaction} commits; nested calls join the open transaction.
 * SQLite shows a transaction its own uncommitted writes, so reads inside it need no extra work.
 */
final class SqliteTransactionContext implements TransactionContext, ZeebeDbTransaction {

  private final SqliteDb<?> db;
  private boolean inTransaction;

  SqliteTransactionContext(final SqliteDb<?> db) {
    this.db = db;
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      if (inTransaction) {
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
    if (!inTransaction) {
      begin();
    }
    return this;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    operations.run();
  }

  @Override
  public void commit() {
    commitInternal();
  }

  @Override
  public void rollback() {
    rollbackInternal();
  }

  /** Called by the database when it closes with this context's transaction still open. */
  void abandon() {
    inTransaction = false;
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    begin();
    try {
      operations.run();
      commitInternal();
    } finally {
      if (inTransaction) {
        rollbackInternal();
      }
    }
  }

  private void begin() {
    db.begin(this);
    inTransaction = true;
  }

  private void commitInternal() {
    if (!inTransaction) {
      return;
    }
    // stays open if the commit fails, so that the caller can still roll back
    db.commit(this);
    inTransaction = false;
  }

  private void rollbackInternal() {
    if (!inTransaction) {
      return;
    }
    inTransaction = false;
    db.rollback(this);
  }
}
