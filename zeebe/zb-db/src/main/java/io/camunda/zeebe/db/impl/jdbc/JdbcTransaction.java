/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

final class JdbcTransaction implements ZeebeDbTransaction {

  /** SQL standard class 40: serialization failure, deadlock, other transaction rollbacks. */
  private static final String SQL_STATE_CLASS_TRANSACTION_ROLLBACK = "40";

  /** SQL standard class 08: connection failures. */
  private static final String SQL_STATE_CLASS_CONNECTION_EXCEPTION = "08";

  private final JdbcConnectionSource connections;
  private final List<JdbcTransactionParticipant> participants = new ArrayList<>();
  private final List<TransactionListener> listeners = new ArrayList<>();
  private boolean inCurrentTransaction;

  JdbcTransaction(final JdbcConnectionSource connections) {
    this.connections = connections;
  }

  void register(final JdbcTransactionParticipant participant) {
    participants.add(participant);
  }

  void addListener(final TransactionListener listener) {
    listeners.add(listener);
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  void begin() {
    inCurrentTransaction = true;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    operations.run();
  }

  /**
   * Flushes all participants and commits. On failure the connection is rolled back but the staged
   * writes are kept, so the stream processor can retry the commit with the same changes. A broken
   * connection is replaced, so the retry flushes them on a new one.
   */
  @Override
  public void commit() throws Exception {
    Connection connection = null;
    try {
      connection = connections.get();
      for (final var participant : participants) {
        participant.flush(connection);
      }
      connection.commit();
    } catch (final SQLException e) {
      if (connection != null) {
        rollbackConnectionQuietly(connection, e);
      }
      if (connections.discardIfBroken(e)) {
        throw new ZeebeDbException("JDBC connection broke during commit, retry on a new one", e);
      }
      throw translate(e);
    }
    resetParticipants();
    inCurrentTransaction = false;
    for (final var listener : listeners) {
      listener.onCommit();
    }
  }

  /**
   * If the connection broke, it is discarded instead of failing the rollback: its open transaction
   * is gone with it.
   */
  @Override
  public void rollback() throws Exception {
    final var connection = connections.current();
    try {
      if (connection != null) {
        connection.rollback();
      }
    } catch (final SQLException e) {
      if (!connections.discardIfBroken(e)) {
        throw translate(e);
      }
    } finally {
      resetParticipants();
      inCurrentTransaction = false;
      for (final var listener : listeners) {
        listener.onRollback();
      }
    }
  }

  static RuntimeException translate(final Exception e) {
    if (isRecoverable(e)) {
      return new ZeebeDbException("Recoverable error during JDBC transaction", e);
    }
    return new RuntimeException("Unexpected error during JDBC transaction", e);
  }

  static boolean isConnectionError(final Throwable error) {
    return anyInChain(
        error,
        e ->
            e instanceof SQLRecoverableException
                || e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException
                || (e instanceof final SQLException sqlException
                    && hasSqlStateClass(
                        sqlException.getSQLState(), SQL_STATE_CLASS_CONNECTION_EXCEPTION)));
  }

  private static boolean isRecoverable(final Throwable error) {
    return anyInChain(
        error,
        e ->
            e instanceof SQLTransientException
                || e instanceof SQLRecoverableException
                || (e instanceof final SQLException sqlException
                    && hasRecoverableSqlState(sqlException.getSQLState())));
  }

  /**
   * Drivers differ in how they report errors: some use the JDBC subclasses, others (e.g.
   * PostgreSQL) throw a generic SQLException and only set the SQLState, and batch execution wraps
   * the actual error in a BatchUpdateException. So we walk the cause and next-exception chains.
   */
  private static boolean anyInChain(final Throwable error, final Predicate<Throwable> matches) {
    final var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    final var pending = new ArrayDeque<Throwable>();
    pending.add(error);
    while (!pending.isEmpty()) {
      final var current = pending.poll();
      if (!seen.add(current)) {
        continue;
      }
      if (matches.test(current)) {
        return true;
      }
      if (current instanceof final SQLException sqlException
          && sqlException.getNextException() != null) {
        pending.add(sqlException.getNextException());
      }
      if (current.getCause() != null) {
        pending.add(current.getCause());
      }
    }
    return false;
  }

  private static boolean hasRecoverableSqlState(final @Nullable String sqlState) {
    return hasSqlStateClass(sqlState, SQL_STATE_CLASS_TRANSACTION_ROLLBACK)
        || hasSqlStateClass(sqlState, SQL_STATE_CLASS_CONNECTION_EXCEPTION);
  }

  private static boolean hasSqlStateClass(final @Nullable String sqlState, final String clazz) {
    return sqlState != null && sqlState.startsWith(clazz);
  }

  private static void rollbackConnectionQuietly(
      final Connection connection, final SQLException cause) {
    try {
      connection.rollback();
    } catch (final SQLException rollbackError) {
      cause.addSuppressed(rollbackError);
    }
  }

  private void resetParticipants() {
    for (final var participant : participants) {
      participant.reset();
    }
  }
}
