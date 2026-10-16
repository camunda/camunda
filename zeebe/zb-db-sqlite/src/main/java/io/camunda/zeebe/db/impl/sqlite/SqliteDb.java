/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/**
 * A {@link ZeebeDb} on a single SQLite file, with one table per column family.
 *
 * <p>Tables are created lazily by {@link #createColumnFamily}, named {@code cf_<name>} after the
 * lower-cased column family constant with every character outside {@code [a-z0-9_]} replaced by
 * {@code _}, see {@link KeyLayout} for the columns. Two constants that map to the same table name
 * are rejected.
 *
 * <p>All access goes through one writer connection. Durability is the Raft log's job, so the
 * connection runs with {@code synchronous=OFF}, like RocksDB runs without its WAL.
 */
public final class SqliteDb<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyType> {

  /** The name of the database file inside the database directory. */
  public static final String DB_FILE_NAME = "zeebe.sqlite";

  private static final Logger LOG = LoggerFactory.getLogger(SqliteDb.class);

  private final Connection connection;
  private final MeterRegistry meterRegistry;
  private final Map<String, PreparedStatement> statements = new HashMap<>();
  private final Map<String, ColumnFamilyType> tables = new HashMap<>();
  private @Nullable SqliteTransactionContext activeContext;

  private SqliteDb(final Connection connection, final MeterRegistry meterRegistry) {
    this.connection = connection;
    this.meterRegistry = meterRegistry;
  }

  static <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily> SqliteDb<T> open(
      final Path directory, final MeterRegistry meterRegistry) {
    Connection connection = null;
    try {
      Files.createDirectories(directory);
      connection =
          DriverManager.getConnection(
              "jdbc:sqlite:" + directory.resolve(DB_FILE_NAME).toAbsolutePath());
      try (final var statement = connection.createStatement()) {
        statement.execute("PRAGMA journal_mode=WAL");
        statement.execute("PRAGMA synchronous=OFF");
        statement.execute("PRAGMA temp_store=MEMORY");
      }
      return new SqliteDb<>(connection, meterRegistry);
    } catch (final SQLException | IOException e) {
      if (connection != null) {
        try {
          connection.close();
        } catch (final SQLException closeFailure) {
          e.addSuppressed(closeFailure);
        }
      }
      throw new IllegalStateException("Unexpected error occurred trying to open the database", e);
    }
  }

  /** The table backing the given column family. */
  static String tableName(final Enum<?> columnFamily) {
    return "cf_" + columnFamily.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    final var table = tableName(columnFamily);
    final var existing = tables.putIfAbsent(table, columnFamily);
    if (existing != null && existing != columnFamily) {
      throw new IllegalStateException(
          "Column families %s and %s both map to the table %s"
              .formatted(existing, columnFamily, table));
    }

    final var layout = KeyLayout.of(keyInstance);
    execute(layout.createTable(table));
    assertTableMatches(table, layout);
    return new SqliteColumnFamily<>(
        this, columnFamily, table, layout, context, keyInstance, valueInstance);
  }

  /**
   * Creates the snapshot with {@code VACUUM INTO}, which cannot run inside a transaction. The
   * snapshot directory contains a database file that {@link SqliteDbFactory#createDb} opens as is.
   */
  @Override
  public void createSnapshot(final File snapshotDir) {
    try {
      Files.createDirectories(snapshotDir.toPath());
      closeStatements();
      final var target = snapshotDir.toPath().resolve(DB_FILE_NAME).toAbsolutePath();
      try (final var statement = connection.createStatement()) {
        statement.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
      }
    } catch (final SQLException | IOException e) {
      throw new ZeebeDbException(
          String.format("Failed to take snapshot in path %s.", snapshotDir), e);
    }
  }

  @Override
  public TransactionContext createContext() {
    return new SqliteTransactionContext(this);
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    final var table = tableName(column);
    final boolean[] empty = {true};
    context.runInTransaction(
        () -> {
          if (!tableExists(table)) {
            return;
          }
          try (final var statement = connection.createStatement();
              final var result =
                  statement.executeQuery("SELECT EXISTS(SELECT 1 FROM " + table + ")")) {
            empty[0] = !result.next() || result.getInt(1) == 0;
          } catch (final SQLException e) {
            throw translate("Failed to check whether " + column + " is empty", e);
          }
        });
    return empty[0];
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

  @Override
  public void close() {
    final var open = activeContext;
    if (open != null) {
      open.abandon();
      activeContext = null;
    }
    closeStatements();
    try {
      connection.close();
    } catch (final SQLException e) {
      LOG.error(
          "Expected to close the SQLite connection successfully, but exception was thrown", e);
    }
  }

  /** The prepared statement for the SQL, which is cached until the database is closed. */
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
   * SQLite JDBC leaves a prepared write statement active after it ran, which makes {@code VACUUM}
   * fail. Closing the cached statements is cheap, they are prepared again on next use.
   */
  private void closeStatements() {
    for (final var statement : statements.values()) {
      try {
        statement.close();
      } catch (final SQLException e) {
        LOG.error("Expected to close a SQLite statement successfully, but exception was thrown", e);
      }
    }
    statements.clear();
  }

  void begin(final SqliteTransactionContext context) {
    if (activeContext != null && activeContext != context) {
      throw new IllegalStateException(
          "Expected no other transaction to be open, but another context has one; all contexts share one writer connection");
    }
    execute("BEGIN");
    activeContext = context;
  }

  void commit(final SqliteTransactionContext context) {
    execute("COMMIT");
    activeContext = null;
  }

  void rollback(final SqliteTransactionContext context) {
    activeContext = null;
    execute("ROLLBACK");
  }

  /** Whether the failure is SQLite's report of a violated primary key. */
  static boolean isPrimaryKeyViolation(final SQLException e) {
    return e instanceof final SQLiteException sqlite
        && (sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY
            || sqlite.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE);
  }

  /** Busy and locked databases are worth retrying, everything else is unexpected. */
  static RuntimeException translate(final String message, final SQLException e) {
    if (e instanceof final SQLiteException sqlite) {
      final var code = sqlite.getResultCode();
      if (code == SQLiteErrorCode.SQLITE_BUSY || code == SQLiteErrorCode.SQLITE_LOCKED) {
        return new ZeebeDbException(message, e);
      }
    }
    return new IllegalStateException(message, e);
  }

  /**
   * A table is created from the first key a column family is used with. Using the same column
   * family with a differently shaped key afterwards, in this or an earlier session, would bind
   * values to columns of the wrong type, so it is rejected.
   */
  private void assertTableMatches(final String table, final KeyLayout layout) {
    final var expected = new ArrayList<String>();
    for (int i = 0; i < layout.columnNames().size(); i++) {
      expected.add(layout.columnNames().get(i) + " " + layout.columnType(i));
    }
    expected.add(KeyLayout.VALUE_COLUMN + " BLOB");

    final var actual = new ArrayList<String>();
    try (final var statement = connection.createStatement();
        final var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (result.next()) {
        actual.add(result.getString("name") + " " + result.getString("type"));
      }
    } catch (final SQLException e) {
      throw translate("Failed to read the columns of table " + table, e);
    }

    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          "Expected the table %s to have the columns %s for this key, but it has %s; a column family must always be used with the same key structure"
              .formatted(table, expected, actual));
    }
  }

  private boolean tableExists(final String table) {
    try (final var statement =
        connection.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
      statement.setString(1, table);
      try (final var result = statement.executeQuery()) {
        return result.next();
      }
    } catch (final SQLException e) {
      throw translate("Failed to look up table " + table, e);
    }
  }

  private void execute(final String sql) {
    try (final Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (final SQLException e) {
      throw translate("Failed to execute " + sql, e);
    }
  }
}
