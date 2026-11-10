/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import java.sql.SQLException;

/**
 * A store that stages its writes in memory during a transaction, writes them to the database when
 * the transaction commits, and owns the database transaction: it takes its connection from a pool
 * when it first accesses the database, and returns it when the transaction ends.
 *
 * <p>Staging lets a store collapse repeated writes to the same row within one transaction into a
 * single statement, and send all statements of a transaction in one batch.
 */
public interface JdbcTransactionParticipant {

  /**
   * Writes all staged changes and commits the database transaction, if the participant accessed the
   * database at all. May be called again for the same staged changes if a previous commit attempt
   * failed, so it must not clear staged state itself.
   */
  void commit() throws SQLException;

  /**
   * Rolls back the database transaction and releases its connection. The staged changes are dropped
   * by {@link #reset()}. Errors are of no interest: a broken connection loses its transaction
   * anyway.
   */
  void rollback();

  /**
   * Called when {@link #commit()} failed. The participant rolls back and releases the connection,
   * so a retry uses a new one. The staged changes are kept for a retry.
   */
  void commitFailed();

  /** Drops all staged changes. Called after a successful commit and after a rollback. */
  void reset();
}
