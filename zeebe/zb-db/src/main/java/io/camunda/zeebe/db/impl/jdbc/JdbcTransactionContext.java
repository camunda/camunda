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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

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
  private final Map<Class<?>, JdbcTransactionParticipant> sharedParticipants = new HashMap<>();

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

  /**
   * Returns the participant of the given type, registering a new one the first time it is asked
   * for, so that several users of this context can share a single participant.
   */
  public <T extends JdbcTransactionParticipant> T participant(
      final Class<T> type, final Supplier<T> factory) {
    final var existing = sharedParticipants.get(type);
    if (existing != null) {
      return type.cast(existing);
    }
    final var created = factory.get();
    sharedParticipants.put(type, created);
    register(created);
    return created;
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
