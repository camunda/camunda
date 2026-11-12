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
 * <p>Like RocksDB's, it is used by one thread at a time and is reused for the next transaction.
 */
final class RdbmsTransaction implements ZeebeDbTransaction {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTransaction.class);

  private final RdbmsStorage storage;
  private final long stateId;
  private final WriteBuffer buffer = new WriteBuffer();
  private @Nullable Connection connection;
  private @Nullable SqlSession session;
  private @Nullable StateMapper mapper;
  private boolean inCurrentTransaction;

  RdbmsTransaction(final RdbmsStorage storage, final long stateId) {
    this.storage = storage;
    this.stateId = stateId;
  }

  long stateId() {
    return stateId;
  }

  /** Returns the value of the key, or {@code null} if it doesn't exist. */
  byte @Nullable [] get(final int columnFamily, final byte[] key) {
    final var write = buffer.get(columnFamily, key);
    if (write != null) {
      final var value = write.value();
      // the caller may wrap and change it, while the buffer still has to send the original
      return value == null ? null : value.clone();
    }
    return query(state -> state.get(stateId, columnFamily, key)).orElse(null);
  }

  void put(final int columnFamily, final byte[] key, final byte[] value) {
    buffer.put(columnFamily, key, value);
  }

  void delete(final int columnFamily, final byte[] key) {
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
        LOG.warn("Failed to roll back the transaction on the partition state {}", stateId, e);
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
          buffer.sendTo(state, stateId);
          session().flushStatements();
        });
    buffer.clear();
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
    final var borrowed = borrowConnection();
    connection = borrowed;
    final var opened = storage.sqlSessionFactory().openSession(ExecutorType.BATCH, borrowed);
    session = opened;
    final var openedMapper = opened.getMapper(StateMapper.class);
    mapper = openedMapper;
    return openedMapper;
  }

  private Connection borrowConnection() {
    @Nullable Connection borrowed = null;
    try {
      borrowed = storage.dataSource().getConnection();
      borrowed.setAutoCommit(false);
      return borrowed;
    } catch (final SQLException e) {
      if (borrowed != null) {
        closeQuietly(borrowed);
      }
      throw SqlErrors.translate(e);
    }
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
      LOG.warn("Failed to return the connection of the partition state {}", stateId, e);
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
