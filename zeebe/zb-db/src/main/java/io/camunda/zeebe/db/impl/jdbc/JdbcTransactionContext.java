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
 * A {@link TransactionContext} backed by a JDBC connection of a {@link JdbcConnectionSource}.
 *
 * <p>The context owns no data itself. Stores register as {@link JdbcTransactionParticipant}s, stage
 * their writes in memory, and read through {@link #connection()} after checking their staged
 * writes. On commit, every participant flushes, then the connection commits.
 *
 * <p>Like the RocksDB context, this is not thread-safe: it is used by the single stream processor
 * actor of a partition.
 */
public final class JdbcTransactionContext implements TransactionContext {

  private final JdbcConnectionSource connections;
  private final JdbcTransaction transaction;

  /** A context on a fixed connection, which is never replaced or closed by this context. */
  public JdbcTransactionContext(final Connection connection) {
    this(JdbcConnectionSource.of(connection));
  }

  /**
   * A context on the connection of the given source, which is replaced by a new one when a commit
   * fails because it broke.
   */
  public JdbcTransactionContext(final JdbcConnectionSource connections) {
    this.connections = connections;
    transaction = new JdbcTransaction(connections);
  }

  /**
   * The connection all reads and flushes of this context's transactions run on. It may change after
   * a failed commit, so it must be looked up again for every use rather than kept.
   */
  public Connection connection() {
    try {
      return connections.get();
    } catch (final SQLException e) {
      throw JdbcTransaction.translate(e);
    }
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
