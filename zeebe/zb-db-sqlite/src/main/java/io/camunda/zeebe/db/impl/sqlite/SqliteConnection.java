/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

/**
 * One JDBC connection with its prepared statements.
 *
 * <p>A prepared statement belongs to its connection, so the statement caches live here. The class
 * is not thread safe, it is used by one thread at a time: the writer connection by the context that
 * holds the write lease, a reader connection by the context it is borrowed by.
 *
 * <p>Point operations share one statement per SQL. Iterations take statements of their own that are
 * not shared while a cursor is open, so that nested iterations do not reset each other.
 */
final class SqliteConnection implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(SqliteConnection.class);

  private final Connection connection;
  private final Map<String, PreparedStatement> statements = new HashMap<>();
  private final Map<String, Deque<PreparedStatement>> idleCursorStatements = new HashMap<>();

  private SqliteConnection(final Connection connection) {
    this.connection = connection;
  }

  /**
   * Opens the connection that all writes go through. Durability is the Raft log's job, so it runs
   * with {@code synchronous=OFF}, like RocksDB runs without its WAL. The WAL journal lets readers
   * run next to the writer.
   */
  static SqliteConnection openWriter(final Path file) throws SQLException {
    final var connection = DriverManager.getConnection(url(file));
    try (final var statement = connection.createStatement()) {
      statement.execute("PRAGMA journal_mode=WAL");
      statement.execute("PRAGMA synchronous=OFF");
      statement.execute("PRAGMA temp_store=MEMORY");
    } catch (final SQLException e) {
      connection.close();
      throw e;
    }
    return new SqliteConnection(connection);
  }

  /** Opens a connection that sees committed state only and cannot write. */
  static SqliteConnection openReader(final Path file) throws SQLException {
    final var config = new SQLiteConfig();
    config.setReadOnly(true);
    final var connection = DriverManager.getConnection(url(file), config.toProperties());
    try (final var statement = connection.createStatement()) {
      statement.execute("PRAGMA temp_store=MEMORY");
    } catch (final SQLException e) {
      connection.close();
      throw e;
    }
    return new SqliteConnection(connection);
  }

  private static String url(final Path file) {
    return "jdbc:sqlite:" + file.toAbsolutePath();
  }

  Connection jdbc() {
    return connection;
  }

  /** The prepared statement for the SQL, which is cached until the connection is closed. */
  PreparedStatement prepare(final String sql) throws SQLException {
    final var cached = statements.get(sql);
    if (cached != null) {
      return cached;
    }
    final var statement = connection.prepareStatement(sql);
    statements.put(sql, statement);
    return statement;
  }

  /**
   * A prepared statement for a cursor over the SQL that no other open cursor uses. Unlike {@link
   * #prepare(String)} this can be called while a cursor with the same SQL is open, which happens
   * when iterations are nested.
   */
  PreparedStatement acquireCursorStatement(final String sql) throws SQLException {
    final var idle = idleCursorStatements.get(sql);
    if (idle != null && !idle.isEmpty()) {
      return idle.pop();
    }
    return connection.prepareStatement(sql);
  }

  /** Hands the statement back once its result set is closed. */
  void releaseCursorStatement(final String sql, final PreparedStatement statement) {
    idleCursorStatements.computeIfAbsent(sql, k -> new ArrayDeque<>()).push(statement);
  }

  void execute(final String sql) throws SQLException {
    try (final var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  /**
   * SQLite JDBC leaves a prepared write statement active after it ran, which makes {@code VACUUM}
   * fail, and statements of a dropped table are useless. Closing the cached statements is cheap,
   * they are prepared again on next use.
   */
  void closeStatements() {
    final var all = new ArrayList<>(statements.values());
    idleCursorStatements.values().forEach(all::addAll);
    for (final var statement : all) {
      try {
        statement.close();
      } catch (final SQLException e) {
        LOG.error("Expected to close a SQLite statement successfully, but exception was thrown", e);
      }
    }
    statements.clear();
    idleCursorStatements.clear();
  }

  @Override
  public void close() {
    closeStatements();
    try {
      connection.close();
    } catch (final SQLException e) {
      LOG.error(
          "Expected to close the SQLite connection successfully, but exception was thrown", e);
    }
  }
}
