/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A store that stages its writes in memory during a transaction and writes them to the database
 * when the transaction commits.
 *
 * <p>Staging lets a store collapse repeated writes to the same row within one transaction into a
 * single statement, and send all statements of a transaction in one batch.
 */
public interface JdbcTransactionParticipant {

  /**
   * Writes all staged changes using the given connection. Called on commit, before the connection
   * commits. May be called again for the same staged changes if a previous commit attempt failed,
   * so it must not clear staged state itself.
   */
  void flush(Connection connection) throws SQLException;

  /** Drops all staged changes. Called after a successful commit and after a rollback. */
  void reset();
}
