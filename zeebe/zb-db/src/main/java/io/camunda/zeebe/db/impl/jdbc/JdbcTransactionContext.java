/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.sql.SQLException;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TransactionContext} for relational stores.
 *
 * <p>The context owns neither data nor connections: it only orchestrates the transaction. Stores
 * register as {@link JdbcTransactionParticipant}s, which stage their writes in memory and run their
 * own database transaction. On commit, every participant commits; on rollback, every participant
 * rolls back.
 *
 * <p>Like the RocksDB context, this is not thread-safe: it is used by the single stream processor
 * actor of a partition.
 */
public final class JdbcTransactionContext implements TransactionContext {

  private final JdbcTransaction transaction = new JdbcTransaction();

  public void register(final JdbcTransactionParticipant participant) {
    transaction.register(participant);
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    call(
        () -> {
          operations.run();
          return null;
        });
  }

  /**
   * Like {@link #runInTransaction(TransactionOperation)}, but returns the result of the operation.
   * Checked exceptions, in particular {@link SQLException}s, are translated like the ones of a
   * commit: a recoverable one becomes a {@link io.camunda.zeebe.db.ZeebeDbException}, any other a
   * runtime exception.
   */
  public <T> @Nullable T call(final JdbcOperation<T> operation) {
    try {
      if (transaction.isInCurrentTransaction()) {
        return operation.run();
      } else {
        return callInNewTransaction(operation);
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

  @Override
  public void addTransactionListener(final TransactionListener listener) {
    transaction.addListener(listener);
  }

  private <T> @Nullable T callInNewTransaction(final JdbcOperation<T> operation) throws Exception {
    transaction.begin();
    try {
      final T result = operation.run();
      transaction.commit();
      return result;
    } catch (final Exception e) {
      try {
        transaction.rollback();
      } catch (final Exception rollbackError) {
        e.addSuppressed(rollbackError);
      }
      throw e;
    }
  }

  /** An operation that runs in a transaction. */
  @FunctionalInterface
  public interface JdbcOperation<T> {
    @Nullable T run() throws Exception;
  }
}
