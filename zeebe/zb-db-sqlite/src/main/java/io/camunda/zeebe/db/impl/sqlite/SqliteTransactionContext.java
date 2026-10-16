/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import org.jspecify.annotations.Nullable;

/**
 * A long-lived handle for one consumer of the database, like the stream processor or a scheduled
 * task. It is used by one thread at a time.
 *
 * <p>Only the outermost {@link #runInTransaction} commits; nested calls join the open transaction.
 *
 * <p>A context does not own a connection. A transaction starts on a reader connection that sees
 * committed state only, and takes the database's write lease and writer connection when it first
 * writes. From then on until it commits or rolls back it reads through the writer connection too,
 * which shows it its own uncommitted writes. A context that has written once takes the lease when
 * its transactions begin, so that an iteration it mutates runs on the writer connection from the
 * start. A context that never writes never takes the lease and never blocks a writer.
 */
final class SqliteTransactionContext implements TransactionContext, ZeebeDbTransaction {

  private final SqliteDb<?> db;
  private boolean inTransaction;
  private boolean holdsWriter;
  private boolean hasWritten;
  private @Nullable SqliteConnection reader;

  SqliteTransactionContext(final SqliteDb<?> db) {
    this.db = db;
  }

  boolean belongsTo(final SqliteDb<?> other) {
    return db == other;
  }

  @Override
  public void runInTransaction(final TransactionOperation operations) {
    try {
      if (inTransaction) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Exception e) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", e);
    }
  }

  @Override
  public ZeebeDbTransaction getCurrentTransaction() {
    if (!inTransaction) {
      begin();
    }
    return this;
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

  /**
   * The connection to read through: the writer if this transaction has written, so that it sees its
   * own changes, else a reader that sees committed state only.
   */
  SqliteConnection read() {
    if (holdsWriter) {
      return db.writer();
    }
    var connection = reader;
    if (connection == null) {
      connection = db.borrowReader();
      reader = connection;
    }
    return connection;
  }

  /**
   * The writer connection, once this transaction holds the write lease. Waits for the lease if
   * another context holds it.
   */
  SqliteConnection write() {
    if (!inTransaction) {
      throw new IllegalStateException("Expected to write inside a transaction, but none is open");
    }
    if (!holdsWriter) {
      db.acquireWriter(this);
      holdsWriter = true;
      hasWritten = true;
    }
    return db.writer();
  }

  /** Called by the database when it closes with this context's transaction still open. */
  void abandon() {
    inTransaction = false;
    holdsWriter = false;
    reader = null;
  }

  private void runInNewTransaction(final TransactionOperation operations) throws Exception {
    begin();
    try {
      operations.run();
      commitInternal();
    } finally {
      if (inTransaction) {
        rollbackInternal();
      }
    }
  }

  private void begin() {
    inTransaction = true;
    if (hasWritten) {
      try {
        write();
      } catch (final RuntimeException e) {
        inTransaction = false;
        throw e;
      }
    }
  }

  private void commitInternal() {
    if (!inTransaction) {
      return;
    }
    if (holdsWriter) {
      // stays open if the commit fails, so that the caller can still roll back
      db.commit(this);
      holdsWriter = false;
    }
    inTransaction = false;
    returnReader();
  }

  private void rollbackInternal() {
    if (!inTransaction) {
      return;
    }
    inTransaction = false;
    if (holdsWriter) {
      holdsWriter = false;
      db.rollback(this);
    }
    returnReader();
  }

  private void returnReader() {
    final var connection = reader;
    if (connection != null) {
      reader = null;
      db.returnReader(connection);
    }
  }
}
