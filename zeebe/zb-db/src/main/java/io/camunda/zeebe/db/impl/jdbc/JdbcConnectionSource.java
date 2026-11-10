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
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JDBC connection that the {@link JdbcTransactionContext}s of one database share.
 *
 * <p>When built from a {@link DataSource}, the connection is borrowed on first use and, after it
 * broke, replaced by a fresh one on the next use; {@link #close()} returns the current connection
 * to the pool. When built from a fixed connection, it is never replaced.
 *
 * <p>Not thread-safe: used by the single stream processor actor of a partition.
 */
public final class JdbcConnectionSource implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcConnectionSource.class);
  private static final int VALIDATION_TIMEOUT_SECONDS = 2;

  private final @Nullable DataSource dataSource;
  private @Nullable Connection current;

  private JdbcConnectionSource(
      final @Nullable DataSource dataSource, final @Nullable Connection fixed) {
    this.dataSource = dataSource;
    current = fixed;
  }

  /** A source that always hands out the given connection, with auto-commit disabled. */
  public static JdbcConnectionSource of(final Connection connection) {
    try {
      connection.setAutoCommit(false);
    } catch (final SQLException e) {
      throw new IllegalStateException("Failed to disable auto-commit on JDBC connection", e);
    }
    return new JdbcConnectionSource(null, connection);
  }

  /** A source that borrows its connection from the given data source and replaces it if broken. */
  public static JdbcConnectionSource from(final DataSource dataSource) {
    return new JdbcConnectionSource(dataSource, null);
  }

  /** The current connection, borrowing a new one if there is none. */
  Connection get() throws SQLException {
    if (current != null) {
      return current;
    }
    if (dataSource == null) {
      throw new SQLException("The JDBC connection was closed", "08003");
    }
    final var connection = dataSource.getConnection();
    try {
      connection.setAutoCommit(false);
    } catch (final SQLException e) {
      closeQuietly(connection);
      throw e;
    }
    current = connection;
    return connection;
  }

  /** The current connection, or null if none is borrowed. */
  @Nullable Connection current() {
    return current;
  }

  /**
   * Discards the current connection if the error shows it is broken, so the next {@link #get()}
   * borrows a new one.
   *
   * @return true if the connection was discarded
   */
  boolean discardIfBroken(final SQLException error) {
    final var connection = current;
    if (dataSource == null || connection == null) {
      return false;
    }
    if (!JdbcTransaction.isConnectionError(error) && isValid(connection)) {
      return false;
    }
    LOG.warn("Discarding broken JDBC connection, borrowing a new one on next use", error);
    current = null;
    closeQuietly(connection);
    return true;
  }

  @Override
  public void close() throws SQLException {
    final var connection = current;
    current = null;
    if (connection != null) {
      connection.close();
    }
  }

  private static boolean isValid(final Connection connection) {
    try {
      return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
    } catch (final SQLException e) {
      return false;
    }
  }

  private static void closeQuietly(final Connection connection) {
    try {
      connection.close();
    } catch (final SQLException e) {
      LOG.debug("Failed to close broken JDBC connection", e);
    }
  }
}
