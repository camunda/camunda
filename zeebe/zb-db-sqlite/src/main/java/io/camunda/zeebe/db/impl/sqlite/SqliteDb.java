/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
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
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
 * <p>Connections: there is one writer connection and a pool of read-only reader connections. The
 * WAL journal lets readers run next to the writer, and a reader sees committed state only. SQLite
 * allows one writing transaction at a time, so the writer connection is guarded by a lease that a
 * {@link SqliteTransactionContext} takes when it first writes and gives back when its transaction
 * ends. The engine has several contexts that write, on different actors (stream processor,
 * exporters, backups), so which context writes is not fixed, they take turns. Contexts that only
 * read, like the scheduled tasks, never wait for the lease.
 *
 * <p>Durability is the Raft log's job, so the writer runs with {@code synchronous=OFF}, like
 * RocksDB runs without its WAL.
 */
public final class SqliteDb<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDb<ColumnFamilyType> {

  /** The name of the database file inside the database directory. */
  public static final String DB_FILE_NAME = "zeebe.sqlite";

  static final Duration DEFAULT_WRITE_LEASE_TIMEOUT = Duration.ofSeconds(10);

  private static final Logger LOG = LoggerFactory.getLogger(SqliteDb.class);

  private final Path file;
  private final SqliteConnection writer;
  private final Semaphore writeLease = new Semaphore(1, true);
  private final Duration writeLeaseTimeout;
  private volatile @Nullable SqliteTransactionContext writeLeaseHolder;
  private final ConcurrentLinkedDeque<SqliteConnection> idleReaders = new ConcurrentLinkedDeque<>();
  private final ConcurrentLinkedDeque<SqliteConnection> allReaders = new ConcurrentLinkedDeque<>();
  private volatile boolean closed;
  private final MeterRegistry meterRegistry;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final Map<String, ColumnFamilyType> tables = new ConcurrentHashMap<>();
  private final Map<String, TableState> tableStates = new ConcurrentHashMap<>();

  private SqliteDb(
      final Path file,
      final SqliteConnection writer,
      final Duration writeLeaseTimeout,
      final MeterRegistry meterRegistry,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.file = file;
    this.writer = writer;
    this.writeLeaseTimeout = writeLeaseTimeout;
    this.meterRegistry = meterRegistry;
    this.consistencyChecksSettings = consistencyChecksSettings;
  }

  static <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily> SqliteDb<T> open(
      final Path directory,
      final MeterRegistry meterRegistry,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    return open(directory, meterRegistry, consistencyChecksSettings, DEFAULT_WRITE_LEASE_TIMEOUT);
  }

  static <T extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily> SqliteDb<T> open(
      final Path directory,
      final MeterRegistry meterRegistry,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final Duration writeLeaseTimeout) {
    try {
      Files.createDirectories(directory);
      final var file = directory.resolve(DB_FILE_NAME);
      final var writer = SqliteConnection.openWriter(file);
      return new SqliteDb<>(
          file, writer, writeLeaseTimeout, meterRegistry, consistencyChecksSettings);
    } catch (final SQLException | IOException e) {
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
    final var sqliteContext = sqliteContext(context);
    final var table = tableName(columnFamily);
    final var existing = tables.putIfAbsent(table, columnFamily);
    if (existing != null && existing != columnFamily) {
      throw new IllegalStateException(
          "Column families %s and %s both map to the table %s"
              .formatted(existing, columnFamily, table));
    }

    final var state = tableState(table, KeyLayout.of(keyInstance), sqliteContext);
    return new SqliteColumnFamily<>(
        this, columnFamily, table, state, sqliteContext, keyInstance, valueInstance);
  }

  /** Whether the checks that are too expensive for production are enabled. */
  boolean preconditionsEnabled() {
    return consistencyChecksSettings.enablePreconditions();
  }

  /**
   * Creates the snapshot with {@code VACUUM INTO}, which cannot run inside a transaction. It runs
   * on a connection of its own, so it neither waits for nor interrupts the writer, and it contains
   * the committed state only. The snapshot directory contains a database file that {@link
   * SqliteDbFactory#createDb} opens as is.
   */
  @Override
  public void createSnapshot(final File snapshotDir) {
    try {
      Files.createDirectories(snapshotDir.toPath());
      final var target = snapshotDir.toPath().resolve(DB_FILE_NAME).toAbsolutePath();
      try (final var snapshotConnection = SqliteConnection.openReader(file)) {
        snapshotConnection.execute("VACUUM INTO '" + target.toString().replace("'", "''") + "'");
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
    final var sqliteContext = sqliteContext(context);
    final var table = tableName(column);
    final boolean[] empty = {true};
    context.runInTransaction(
        () -> {
          final var connection = sqliteContext.read();
          if (!tableExists(connection, table)) {
            return;
          }
          try (final var statement = connection.jdbc().createStatement();
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
    closed = true;
    final var holder = writeLeaseHolder;
    if (holder != null) {
      holder.abandon();
      writeLeaseHolder = null;
    }
    SqliteConnection reader;
    while ((reader = allReaders.pollFirst()) != null) {
      reader.close();
    }
    idleReaders.clear();
    writer.close();
  }

  /** The writer connection, to be used only by the context that holds the write lease. */
  SqliteConnection writer() {
    return writer;
  }

  /** An idle reader connection, or a new one if all are in use. */
  SqliteConnection borrowReader() {
    if (closed) {
      throw new IllegalStateException("Expected to read from the database, but it is closed");
    }
    final var idle = idleReaders.pollFirst();
    if (idle != null) {
      return idle;
    }
    try {
      final var reader = SqliteConnection.openReader(file);
      allReaders.add(reader);
      return reader;
    } catch (final SQLException e) {
      throw translate("Failed to open a reader connection", e);
    }
  }

  void returnReader(final SqliteConnection reader) {
    if (closed) {
      // the database closed while the reader was borrowed
      allReaders.remove(reader);
      reader.close();
    } else {
      idleReaders.push(reader);
    }
  }

  /**
   * Gives the context the write lease and starts its transaction on the writer connection. Waits
   * for the context that holds the lease to commit or roll back.
   */
  void acquireWriter(final SqliteTransactionContext context) {
    if (closed) {
      throw new IllegalStateException("Expected to write to the database, but it is closed");
    }
    final boolean acquired;
    try {
      acquired = writeLease.tryAcquire(writeLeaseTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for the SQLite write lease, which another transaction context holds",
          e);
    }
    if (!acquired) {
      throw new IllegalStateException(
          ("Expected to acquire the SQLite write lease within %s to write, but another transaction context still holds it. "
                  + "SQLite has one writer, so a transaction that writes waits for the transaction of every other context that writes; "
                  + "a context must not write while a transaction of another context on the same thread is open")
              .formatted(writeLeaseTimeout));
    }
    try {
      writer.execute("BEGIN");
    } catch (final SQLException e) {
      writeLease.release();
      throw translate("Failed to begin a transaction", e);
    }
    writeLeaseHolder = context;
  }

  void commit(final SqliteTransactionContext context) {
    try {
      writer.execute("COMMIT");
    } catch (final SQLException e) {
      // the lease stays with the context, so that it can roll back
      throw translate("Failed to execute COMMIT", e);
    }
    releaseWriter();
  }

  void rollback(final SqliteTransactionContext context) {
    try {
      writer.execute("ROLLBACK");
    } catch (final SQLException e) {
      throw translate("Failed to execute ROLLBACK", e);
    } finally {
      releaseWriter();
    }
  }

  private void releaseWriter() {
    writeLeaseHolder = null;
    writeLease.release();
  }

  private SqliteTransactionContext sqliteContext(final TransactionContext context) {
    if (context instanceof final SqliteTransactionContext sqliteContext
        && sqliteContext.belongsTo(this)) {
      return sqliteContext;
    }
    throw new IllegalArgumentException(
        "Expected a transaction context created by this SQLite database, but got " + context);
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
   * The state of the table of a column family, which is created from the first key the column
   * family is used with.
   *
   * <p>A table with typed columns can hold only one key shape, but the engine uses the same column
   * family with an old and a new key shape while it migrates, see the legacy states of the 8.3
   * multi-tenancy migration. When a second shape arrives, the table falls back to a single opaque
   * {@code k BLOB PRIMARY KEY} column that holds the serialized key. SQLite orders {@code BLOB}s
   * with {@code memcmp} like RocksDB orders its keys, so only queryability is lost.
   *
   * <p>If the table holds rows already, they are rewritten in the opaque form, so they stay
   * readable. That needs the layout the rows were written with, which is only known from earlier in
   * this session. A typed table from an earlier session that does not match the key is therefore
   * rejected, as binding a key to the wrong columns would corrupt it silently.
   *
   * <p>The schema changes run on the writer connection in the transaction of the context, which
   * joins its open transaction if it has one.
   */
  private TableState tableState(
      final String table, final KeyLayout layout, final SqliteTransactionContext context) {
    final var known = tableStates.get(table);
    if (known != null && (known.layout().isOpaque() || known.layout().sameShape(layout))) {
      return known;
    }
    final TableState[] resolved = new TableState[1];
    context.runInTransaction(
        () -> resolved[0] = resolveTableState(table, layout, context, tableStates.get(table)));
    return resolved[0];
  }

  private TableState resolveTableState(
      final String table,
      final KeyLayout layout,
      final SqliteTransactionContext context,
      final @Nullable TableState known) {
    if (known != null) {
      if (!known.layout().isOpaque() && !known.layout().sameShape(layout)) {
        migrateToOpaque(table, known, context.write());
      }
      return known;
    }

    var actual = columns(context.read(), table);
    final var state = new TableState(layout);
    if (actual.isEmpty()) {
      // another context may have created the table while this one waited for the write lease
      final var connection = context.write();
      actual = columns(connection, table);
      if (actual.isEmpty()) {
        execute(connection, layout.createTable(table));
        actual = expectedColumns(layout);
      }
    }
    if (!actual.equals(expectedColumns(layout))) {
      if (!actual.equals(expectedColumns(layout.asOpaque()))) {
        throw new IllegalStateException(
            "Expected the table %s to have the columns %s for this key, but it has %s; a column family must always be used with the same key structure"
                .formatted(table, expectedColumns(layout), actual));
      }
      state.layout(layout.asOpaque());
    }
    final var raced = tableStates.putIfAbsent(table, state);
    return raced != null ? raced : state;
  }

  /**
   * Rewrites the table with a single opaque key column. It runs in a savepoint, so it joins an open
   * transaction and either completes entirely or not at all.
   */
  private void migrateToOpaque(
      final String table, final TableState state, final SqliteConnection writer) {
    final var old = state.layout();
    final var opaque = old.asOpaque();
    final var migrated = table + "_opaque";
    LOG.info(
        "Column family table {} is used with more than one key structure, storing its keys as opaque blobs",
        table);

    writer.closeStatements();
    execute(writer, "SAVEPOINT zb_opaque_migration");
    try {
      execute(writer, "DROP TABLE IF EXISTS " + migrated);
      execute(writer, opaque.createTable(migrated));
      try (final var select = writer.jdbc().createStatement();
          final var rows =
              select.executeQuery(
                  "SELECT %s, %s FROM %s"
                      .formatted(
                          String.join(", ", old.columnNames()), KeyLayout.VALUE_COLUMN, table));
          final var insert =
              writer
                  .jdbc()
                  .prepareStatement(
                      "INSERT INTO %s (%s, %s) VALUES (?, ?)"
                          .formatted(
                              migrated, opaque.columnNames().get(0), KeyLayout.VALUE_COLUMN))) {
        final int valueColumn = old.columnNames().size() + 1;
        while (rows.next()) {
          insert.setBytes(1, old.readKey(rows, 1));
          insert.setBytes(2, rows.getBytes(valueColumn));
          insert.executeUpdate();
        }
      }
      execute(writer, "DROP TABLE " + table);
      execute(writer, "ALTER TABLE %s RENAME TO %s".formatted(migrated, table));
      execute(writer, "RELEASE zb_opaque_migration");
    } catch (final SQLException | RuntimeException e) {
      try {
        execute(writer, "ROLLBACK TO zb_opaque_migration");
        execute(writer, "RELEASE zb_opaque_migration");
      } catch (final RuntimeException rollbackFailure) {
        e.addSuppressed(rollbackFailure);
      }
      if (e instanceof final SQLException sql) {
        throw translate("Failed to store the keys of table " + table + " as opaque blobs", sql);
      }
      throw (RuntimeException) e;
    }
    state.layout(opaque);
  }

  private List<String> expectedColumns(final KeyLayout layout) {
    final var expected = new ArrayList<String>();
    for (int i = 0; i < layout.columnNames().size(); i++) {
      expected.add(layout.columnNames().get(i) + " " + layout.columnType(i));
    }
    expected.add(KeyLayout.VALUE_COLUMN + " BLOB");
    return expected;
  }

  /** The names and types of the columns of the table, empty if it does not exist. */
  private List<String> columns(final SqliteConnection connection, final String table) {
    final var actual = new ArrayList<String>();
    try (final var statement = connection.jdbc().createStatement();
        final var result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (result.next()) {
        actual.add(result.getString("name") + " " + result.getString("type"));
      }
    } catch (final SQLException e) {
      throw translate("Failed to read the columns of table " + table, e);
    }
    return actual;
  }

  private boolean tableExists(final SqliteConnection connection, final String table) {
    try (final var statement =
        connection
            .jdbc()
            .prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
      statement.setString(1, table);
      try (final var result = statement.executeQuery()) {
        return result.next();
      }
    } catch (final SQLException e) {
      throw translate("Failed to look up table " + table, e);
    }
  }

  private void execute(final SqliteConnection connection, final String sql) {
    try {
      connection.execute(sql);
    } catch (final SQLException e) {
      throw translate("Failed to execute " + sql, e);
    }
  }
}
