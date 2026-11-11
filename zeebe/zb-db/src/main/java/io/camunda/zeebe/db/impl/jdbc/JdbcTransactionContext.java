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
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A {@link TransactionContext} for relational stores.
 *
 * <p>The context owns neither data nor connections: it only orchestrates the transaction. Stores
 * register as {@link JdbcTransactionParticipant}s, which stage their writes in memory and run their
 * own database transaction. On commit, every participant commits; on rollback, every participant
 * rolls back.
 *
 * <p>Unlike RocksDB, whose state is restored from a snapshot and rebuilt by replaying the log, the
 * database keeps every committed change across restarts. So each transaction of the stream
 * processor is {@link #markAsProcessed marked} with the position of its command: the participants
 * store it with the changes, and a transaction whose position the database already contains is
 * discarded on commit instead, as its changes were committed before.
 *
 * <p>Like the RocksDB context, this is not thread-safe: it is used by the single stream processor
 * actor of a partition.
 */
public final class JdbcTransactionContext implements TransactionContext {

  /** The position of a database that contains no processed command. */
  public static final long NO_POSITION = -1L;

  private final JdbcTransaction transaction;

  /** A context whose transactions are all committed, as the database contains no command yet. */
  public JdbcTransactionContext() {
    this(NO_POSITION);
  }

  /**
   * @param committedPosition the last processed position the database contains, read when the
   *     stream processor starts; transactions marked with this or a lower position are discarded
   */
  public JdbcTransactionContext(final long committedPosition) {
    transaction = new JdbcTransaction(committedPosition);
  }

  public void register(final JdbcTransactionParticipant participant) {
    transaction.register(participant);
  }

  /**
   * Returns the registered participant of the type, or registers the one that {@code create}
   * returns. Stores that are built separately but must commit together, like the engine state and
   * the key generator of a partition, share one participant this way.
   */
  public <T extends JdbcTransactionParticipant> T participant(
      final Class<T> type, final Supplier<T> create) {
    return transaction.participant(type, create);
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

  /**
   * Marks the current transaction as the one that applies the changes of the command at the given
   * position, as the stream processor does when it processes the command or replays its events. If
   * the database already contains this position, the transaction's changes are discarded on commit;
   * otherwise the participants write the position with them.
   */
  public void markAsProcessed(final long position) {
    call(
        () -> {
          transaction.markAsProcessed(position);
          return null;
        });
  }

  /**
   * Marks the current transaction as the replay of the events of the command at the given position.
   * If the database already contains this position, the transaction {@link
   * #isReplayingCommittedChanges() replays committed changes} until it ends: the relational stores
   * must then not change the rows, which are already in the later state of the database's position.
   */
  @Override
  public void markAsReplaying(final long sourcePosition) {
    transaction.markAsReplaying(sourcePosition);
  }

  /**
   * Whether the current transaction replays events whose changes the database already contains, see
   * {@link #markAsReplaying(long)}.
   */
  public boolean isReplayingCommittedChanges() {
    return transaction.isReplayingCommittedChanges();
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
