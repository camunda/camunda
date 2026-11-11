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
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

final class JdbcTransaction implements ZeebeDbTransaction {

  /** SQL standard class 40: serialization failure, deadlock, other transaction rollbacks. */
  private static final String SQL_STATE_CLASS_TRANSACTION_ROLLBACK = "40";

  /** SQL standard class 08: connection failures. */
  private static final String SQL_STATE_CLASS_CONNECTION_EXCEPTION = "08";

  private final List<JdbcTransactionParticipant> participants = new ArrayList<>();
  private final List<TransactionListener> listeners = new ArrayList<>();
  private final long committedPosition;
  private long processedPosition = JdbcTransactionContext.NO_POSITION;
  private long replayedPosition = JdbcTransactionContext.NO_POSITION;
  private boolean inCurrentTransaction;

  /**
   * @param committedPosition the last processed position the database contains, see {@link
   *     JdbcTransactionContext#JdbcTransactionContext(long)}
   */
  JdbcTransaction(final long committedPosition) {
    this.committedPosition = committedPosition;
  }

  void register(final JdbcTransactionParticipant participant) {
    participants.add(participant);
  }

  <T extends JdbcTransactionParticipant> T participant(
      final Class<T> type, final Supplier<T> create) {
    for (final var participant : participants) {
      if (type.isInstance(participant)) {
        return type.cast(participant);
      }
    }
    final var participant = create.get();
    participants.add(participant);
    return participant;
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

  void markAsProcessed(final long position) {
    processedPosition = position;
    for (final var participant : participants) {
      participant.markAsProcessed(position);
    }
  }

  void markAsReplaying(final long sourcePosition) {
    replayedPosition = sourcePosition;
  }

  boolean isReplayingCommittedChanges() {
    return replayedPosition != JdbcTransactionContext.NO_POSITION
        && replayedPosition <= committedPosition;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    operations.run();
  }

  /**
   * Commits all participants. On failure they roll back but keep their staged writes, so the stream
   * processor can retry the commit with the same changes, on a new connection.
   *
   * <p>A transaction marked with a position the database already contains is rolled back instead:
   * it re-applies changes that were committed before a restart.
   */
  @Override
  public void commit() throws Exception {
    if (processedPosition != JdbcTransactionContext.NO_POSITION
        && processedPosition <= committedPosition) {
      rollback();
      return;
    }
    try {
      for (final var participant : participants) {
        participant.commit();
      }
    } catch (final SQLException e) {
      for (final var participant : participants) {
        participant.commitFailed();
      }
      throw translate(e);
    }
    resetParticipants();
    inCurrentTransaction = false;
    processedPosition = JdbcTransactionContext.NO_POSITION;
    replayedPosition = JdbcTransactionContext.NO_POSITION;
    for (final var listener : listeners) {
      listener.onCommit();
    }
  }

  @Override
  public void rollback() throws Exception {
    try {
      for (final var participant : participants) {
        participant.rollback();
      }
    } finally {
      resetParticipants();
      inCurrentTransaction = false;
      processedPosition = JdbcTransactionContext.NO_POSITION;
      replayedPosition = JdbcTransactionContext.NO_POSITION;
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

  private void resetParticipants() {
    for (final var participant : participants) {
      participant.reset();
    }
  }
}
