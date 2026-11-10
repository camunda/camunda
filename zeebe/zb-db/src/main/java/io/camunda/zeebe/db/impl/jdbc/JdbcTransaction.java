/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class JdbcTransaction implements ZeebeDbTransaction {

  /** SQL standard class 40: serialization failure, deadlock, other transaction rollbacks. */
  private static final String SQL_STATE_CLASS_TRANSACTION_ROLLBACK = "40";

  /** SQL standard class 08: connection failures. */
  private static final String SQL_STATE_CLASS_CONNECTION_EXCEPTION = "08";

  private final Connection connection;
  private final List<JdbcTransactionParticipant> participants = new ArrayList<>();
  private boolean inCurrentTransaction;

  JdbcTransaction(final Connection connection) {
    this.connection = connection;
  }

  void register(final JdbcTransactionParticipant participant) {
    participants.add(participant);
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
   * writes are kept, so the stream processor can retry the commit with the same changes.
   */
  @Override
  public void commit() throws Exception {
    try {
      for (final var participant : participants) {
        participant.flush(connection);
      }
      connection.commit();
    } catch (final SQLException e) {
      rollbackConnectionQuietly(e);
      throw translate(e);
    }
    resetParticipants();
    inCurrentTransaction = false;
  }

  @Override
  public void rollback() throws Exception {
    try {
      connection.rollback();
    } catch (final SQLException e) {
      throw translate(e);
    } finally {
      resetParticipants();
      inCurrentTransaction = false;
    }
  }

  static RuntimeException translate(final Exception e) {
    if (isRecoverable(e)) {
      return new ZeebeDbException("Recoverable error during JDBC transaction", e);
    }
    return new RuntimeException("Unexpected error during JDBC transaction", e);
  }

  /**
   * Drivers differ in how they report retryable errors: some use the JDBC subclasses, others (e.g.
   * PostgreSQL) throw a generic SQLException and only set the SQLState, and batch execution wraps
   * the actual error in a BatchUpdateException. So we walk the cause and next-exception chains and
   * accept either signal.
   */
  private static boolean isRecoverable(final Throwable error) {
    final var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    final var pending = new ArrayDeque<Throwable>();
    pending.add(error);
    while (!pending.isEmpty()) {
      final var current = pending.poll();
      if (!seen.add(current)) {
        continue;
      }
      if (current instanceof SQLTransientException || current instanceof SQLRecoverableException) {
        return true;
      }
      if (current instanceof final SQLException sqlException) {
        if (hasRecoverableSqlState(sqlException.getSQLState())) {
          return true;
        }
        if (sqlException.getNextException() != null) {
          pending.add(sqlException.getNextException());
        }
      }
      if (current.getCause() != null) {
        pending.add(current.getCause());
      }
    }
    return false;
  }

  private static boolean hasRecoverableSqlState(final @Nullable String sqlState) {
    return sqlState != null
        && (sqlState.startsWith(SQL_STATE_CLASS_TRANSACTION_ROLLBACK)
            || sqlState.startsWith(SQL_STATE_CLASS_CONNECTION_EXCEPTION));
  }

  private void rollbackConnectionQuietly(final SQLException cause) {
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
