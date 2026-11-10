/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A {@link TransactionContext} backed by a single JDBC connection.
 *
 * <p>The context owns no data itself. Stores register as {@link JdbcTransactionParticipant}s, stage
 * their writes in memory, and read through {@link #connection()} after checking their staged
 * writes. On commit, every participant flushes, then the connection commits.
 *
 * <p>Like the RocksDB context, this is not thread-safe: it is used by the single stream processor
 * actor of a partition.
 */
public final class JdbcTransactionContext implements TransactionContext {

  private final Connection connection;
  private final JdbcTransaction transaction;

  public JdbcTransactionContext(final Connection connection) {
    this.connection = connection;
    try {
      connection.setAutoCommit(false);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to disable auto-commit on JDBC connection", e);
    }
    transaction = new JdbcTransaction(connection);
  }

  /** The connection all reads and flushes of this context's transactions run on. */
  public Connection connection() {
    return connection;
  }

  public void register(final JdbcTransactionParticipant participant) {
    transaction.register(participant);
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
      throw JdbcTransaction.translate(e);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    if (!transaction.isInCurrentTransaction()) {
      transaction.begin();
    }
    return transaction;
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    transaction.begin();
    try {
      operations.run();
      transaction.commit();
    } catch (final Exception e) {
      try {
        transaction.rollback();
      } catch (final Exception rollbackError) {
        e.addSuppressed(rollbackError);
      }
      throw e;
    }
  }
}
