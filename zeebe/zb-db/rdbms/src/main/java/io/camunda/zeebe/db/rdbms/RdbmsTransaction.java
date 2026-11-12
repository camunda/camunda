/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.SqlSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A database transaction on the state of one partition.
 *
 * <p>It borrows a connection only when it first needs the database, so a transaction that only
 * writes holds none until it commits. Writes wait in a {@link WriteBuffer} and go to the database
 * as one batch before every scan and at commit. Point reads look at the buffer first.
 *
 * <p>Before its first writes reach the database, it checks that its term still owns the state, and
 * locks the state row until it ends. A newer leader's claim waits for it, and once claimed, writes
 * of the older term throw {@link StateFencedException}. Transactions that only read never check.
 * Without a term, it only reads: writing throws right away.
 *
 * <p>Like RocksDB's, it is used by one thread at a time and is reused for the next transaction.
 */
final class RdbmsTransaction implements ZeebeDbTransaction {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTransaction.class);

  private final RdbmsStorage storage;
  private final int partitionId;
  private final String stateId;
  private final OptionalLong term;
  private final WriteBuffer buffer = new WriteBuffer();
  private @Nullable Connection connection;
  private @Nullable SqlSession session;
  private @Nullable StateMapper mapper;
  private boolean inCurrentTransaction;
  private boolean ownerChecked;

  /**
   * @param stateId the state the term must own to write
   * @param term the term to write with, or empty to only read
   */
  RdbmsTransaction(
      final RdbmsStorage storage,
      final int partitionId,
      final String stateId,
      final OptionalLong term) {
    this.storage = storage;
    this.partitionId = partitionId;
    this.stateId = stateId;
    this.term = term;
  }

  int partitionId() {
    return partitionId;
  }

  /** Returns the value of the key, or {@code null} if it doesn't exist. */
  byte @Nullable [] get(final int columnFamily, final byte[] key) {
    final var write = buffer.get(columnFamily, key);
    if (write != null) {
      final var value = write.value();
      // the caller may wrap and change it, while the buffer still has to send the original
      return value == null ? null : value.clone();
    }
    final var table = StateTables.of(columnFamily);
    return query(state -> state.get(partitionId, table.name(), table.shared(), columnFamily, key))
        .orElse(null);
  }

  void put(final int columnFamily, final byte[] key, final byte[] value) {
    checkWritable();
    buffer.put(columnFamily, key, value);
  }

  void delete(final int columnFamily, final byte[] key) {
    checkWritable();
    buffer.delete(columnFamily, key);
  }

  /**
   * Visits the rows of the range in its order, until the visitor returns {@code false}. With {@code
   * keysOnly}, the visitor gets {@code null} for every value. Writes made while the scan runs don't
   * show up in it.
   */
  void scan(final StateRange range, final boolean keysOnly, final RowVisitor visitor) {
    flush();
    final var handler = new VisitingHandler(visitor);
    execute(
        state -> {
          if (keysOnly) {
            state.scanKeys(
                range, context -> handler.visit(context, context.getResultObject(), null));
          } else {
            state.scan(
                range,
                context -> {
                  final var row = context.getResultObject();
                  handler.visit(context, row.key(), row.value());
                });
          }
        });
    handler.rethrowFailure();
  }

  long count(final StateRange range) {
    flush();
    return query(state -> state.count(range));
  }

  boolean exists(final StateRange range) {
    flush();
    return query(state -> state.exists(range));
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  void resetTransaction() {
    rollbackInternal();
    inCurrentTransaction = true;
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

  void commitInternal() {
    inCurrentTransaction = false;
    flush();
    final var current = connection;
    if (current != null) {
      try {
        current.commit();
      } catch (final SQLException e) {
        throw SqlErrors.translate(e);
      }
      release();
    }
  }

  /**
   * Discards the writes and rolls the database transaction back. It doesn't throw: once the
   * connection is gone, the database rolls back by itself.
   */
  void rollbackInternal() {
    inCurrentTransaction = false;
    buffer.clear();
    final var current = connection;
    if (current != null) {
      try {
        current.rollback();
      } catch (final SQLException e) {
        LOG.warn(
            "Failed to roll back the transaction on the state of partition {}", partitionId, e);
      } finally {
        release();
      }
    }
  }

  private void flush() {
    if (buffer.isEmpty()) {
      return;
    }
    execute(
        state -> {
          if (!ownerChecked) {
            checkOwner(state);
            ownerChecked = true;
          }
          buffer.sendTo(state, partitionId);
          session().flushStatements();
        });
    buffer.clear();
  }

  private void checkWritable() {
    if (term.isEmpty()) {
      throw new UnsupportedOperationException(
          "Expected to write to the state of partition %d, but it was opened for reading only"
              .formatted(partitionId));
    }
  }

  private void checkOwner(final StateMapper state) {
    final long writeTerm = term.orElseThrow();
    final var ownerTerm = state.lockOwnerTerm(partitionId, stateId);
    if (ownerTerm.isEmpty() || ownerTerm.get() != writeTerm) {
      throw new StateFencedException(
          "Expected to write to the state %s of partition %d with term %d, but %s"
              .formatted(
                  stateId,
                  partitionId,
                  writeTerm,
                  ownerTerm
                      .map(owner -> "a leader of term " + owner + " owns it")
                      .orElse("the state doesn't exist")));
    }
  }

  /** Runs statements on this transaction's connection. */
  private <T> T query(final Function<StateMapper, T> statements) {
    final var state = mapper();
    try {
      return statements.apply(state);
    } catch (final PersistenceException e) {
      throw SqlErrors.translate(e);
    }
  }

  private void execute(final Consumer<StateMapper> statements) {
    query(
        state -> {
          statements.accept(state);
          return Boolean.TRUE;
        });
  }

  private StateMapper mapper() {
    final var current = mapper;
    if (current != null) {
      return current;
    }
    final var borrowed = storage.borrowConnection();
    connection = borrowed;
    final var opened = storage.sqlSessionFactory().openSession(ExecutorType.BATCH, borrowed);
    session = opened;
    final var openedMapper = opened.getMapper(StateMapper.class);
    mapper = openedMapper;
    return openedMapper;
  }

  private SqlSession session() {
    final var current = session;
    if (current == null) {
      throw new IllegalStateException("Expected an open session, but the transaction has none");
    }
    return current;
  }

  /** Closes the session, which leaves the connection open, then returns the connection. */
  private void release() {
    final var openSession = session;
    final var borrowed = connection;
    session = null;
    mapper = null;
    connection = null;
    ownerChecked = false;
    if (openSession != null) {
      openSession.close();
    }
    if (borrowed != null) {
      closeQuietly(borrowed);
    }
  }

  private void closeQuietly(final Connection borrowed) {
    try {
      borrowed.close();
    } catch (final SQLException e) {
      LOG.warn("Failed to return the connection of the state of partition {}", partitionId, e);
    }
  }

  /** Visits a key and its value, or {@code null} for a scan of keys only. */
  @FunctionalInterface
  interface RowVisitor {
    boolean visit(byte[] key, byte @Nullable [] value);
  }

  /**
   * Stops the query when the visitor returns {@code false} or throws. MyBatis would wrap what the
   * visitor throws, so it is kept and thrown as it is once the query is over.
   */
  private static final class VisitingHandler {

    private final RowVisitor visitor;
    private @Nullable Throwable failure;

    private VisitingHandler(final RowVisitor visitor) {
      this.visitor = visitor;
    }

    private void visit(
        final ResultContext<?> context, final byte[] key, final byte @Nullable [] value) {
      try {
        if (!visitor.visit(key, value)) {
          context.stop();
        }
      } catch (final RuntimeException | Error e) {
        failure = e;
        context.stop();
      }
    }

    private void rethrowFailure() {
      final var thrown = failure;
      if (thrown instanceof final RuntimeException e) {
        throw e;
      }
      if (thrown instanceof final Error e) {
        throw e;
      }
    }
  }
}
