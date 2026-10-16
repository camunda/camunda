/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.sqlite.KeyLayout.PrefixShape;
import io.camunda.zeebe.db.impl.sqlite.KeyLayout.ScanSpec;
import io.camunda.zeebe.db.impl.sqlite.TableState.Cursor;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Point operations, counting and iteration on the table of one column family.
 *
 * <p>Prepared statements are created on first use and cached by the connection. Iterations use
 * statements of their own, which are not shared while a cursor is open, so that they can be nested.
 *
 * <p>SQLite cursors tolerate the deletion of the key they just visited but silently skip rows when
 * a key ahead of them is deleted, which RocksDB iterators do not. With preconditions enabled, such
 * a delete is rejected.
 */
final class SqliteColumnFamily<
        ColumnFamilyType extends Enum<?> & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  /** The same limit as RocksDB's, which has two prefix buffers per column family. */
  private static final int MAX_NESTED_ITERATIONS = 2;

  private final SqliteDb<?> db;
  private final ColumnFamilyType columnFamily;
  private final String table;
  private final TableState state;
  private KeyLayout layout;
  private final SqliteTransactionContext context;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private String insertSql;
  private String updateSql;
  private String upsertSql;
  private String getSql;
  private String deleteSql;
  private String existsSql;
  private String isEmptySql;
  private String countSql;
  private final Map<PrefixShape, String> countPrefixSql = new HashMap<>();
  private final Map<ScanSpec, String> scanSql = new HashMap<>();
  private int iterationDepth;

  SqliteColumnFamily(
      final SqliteDb<?> db,
      final ColumnFamilyType columnFamily,
      final String table,
      final TableState state,
      final SqliteTransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.db = db;
    this.columnFamily = columnFamily;
    this.table = table;
    this.state = state;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    layout = state.layout();
    buildSql();
  }

  /** The layout of the table changes if it falls back to an opaque key, the SQL follows. */
  private void buildSql() {
    final var columns = layout.columnNames();
    final var where = String.join(" AND ", columns.stream().map(c -> c + " = ?").toList());
    final var keyColumns = String.join(", ", columns);
    final var placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
    final var insert =
        "INSERT INTO %s (%s, %s) VALUES (%s, ?)"
            .formatted(table, keyColumns, KeyLayout.VALUE_COLUMN, placeholders);
    insertSql = insert;
    upsertSql =
        "%s ON CONFLICT (%s) DO UPDATE SET %s = excluded.%s"
            .formatted(insert, keyColumns, KeyLayout.VALUE_COLUMN, KeyLayout.VALUE_COLUMN);
    updateSql = "UPDATE %s SET %s = ? WHERE %s".formatted(table, KeyLayout.VALUE_COLUMN, where);
    getSql = "SELECT %s FROM %s WHERE %s".formatted(KeyLayout.VALUE_COLUMN, table, where);
    deleteSql = "DELETE FROM %s WHERE %s".formatted(table, where);
    existsSql = "SELECT 1 FROM %s WHERE %s".formatted(table, where);
    isEmptySql = "SELECT EXISTS(SELECT 1 FROM %s)".formatted(table);
    countSql = "SELECT count(*) FROM " + table;
    countPrefixSql.clear();
    scanSql.clear();
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamily.partitionScope();
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    inTransaction(
        () -> {
          final var keyLength = key.write(keyBuffer, 0);
          final var valueBytes = valueBytes(value);
          final var statement = writeStatement(insertSql);
          final int next = layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
          statement.setBytes(next, valueBytes);
          try {
            statement.executeUpdate();
          } catch (final SQLException e) {
            if (SqliteDb.isPrimaryKeyViolation(e)) {
              throw new ZeebeDbInconsistentException(
                  "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
            }
            throw e;
          }
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    inTransaction(
        () -> {
          final var keyLength = key.write(keyBuffer, 0);
          final var statement = writeStatement(updateSql);
          statement.setBytes(1, valueBytes(value));
          layout.bindKey(statement, 2, keyBuffer.byteArray(), keyLength);
          if (statement.executeUpdate() == 0) {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
          }
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    inTransaction(
        () -> {
          final var keyLength = key.write(keyBuffer, 0);
          final var valueBytes = valueBytes(value);
          final var statement = writeStatement(upsertSql);
          final int next = layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
          statement.setBytes(next, valueBytes);
          statement.executeUpdate();
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    final var bytes = read(key);
    if (bytes == null) {
      return null;
    }
    valueInstance.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return valueInstance;
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final var bytes = read(key);
    if (bytes == null) {
      return null;
    }
    final var value = valueSupplier.get();
    value.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return value;
  }

  @Override
  public void deleteExisting(final KeyType key) {
    inTransaction(
        () -> {
          if (delete(key) == 0) {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
          }
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    inTransaction(() -> delete(key));
  }

  @Override
  public boolean exists(final KeyType key) {
    final boolean[] exists = new boolean[1];
    inTransaction(
        () -> {
          final var keyLength = key.write(keyBuffer, 0);
          final var statement = readStatement(existsSql);
          layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
          try (final var result = statement.executeQuery()) {
            exists[0] = result.next();
          }
        });
    return exists[0];
  }

  @Override
  public boolean isEmpty() {
    final boolean[] empty = new boolean[1];
    inTransaction(
        () -> {
          try (final var result = readStatement(isEmptySql).executeQuery()) {
            empty[0] = !result.next() || result.getInt(1) == 0;
          }
        });
    return empty[0];
  }

  @Override
  public long count() {
    final long[] count = new long[1];
    inTransaction(
        () -> {
          try (final var result = readStatement(countSql).executeQuery()) {
            count[0] = result.next() ? result.getLong(1) : 0;
          }
        });
    return count[0];
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    final long[] count = new long[1];
    inTransaction(
        () -> {
          final var prefixLength = prefix.write(keyBuffer, 0);
          final var prefixBytes = keyBuffer.byteArray();
          final var shape = layout.prefixShape(prefixBytes, prefixLength);
          final var sql = countPrefixSql.computeIfAbsent(shape, this::countPrefixSql);
          final var statement = readStatement(sql);
          layout.bindPrefix(statement, shape, prefixBytes, prefixLength);
          try (final var result = statement.executeQuery()) {
            count[0] = result.next() ? result.getLong(1) : 0;
          }
        });
    return count[0];
  }

  private String countPrefixSql(final PrefixShape shape) {
    final var condition = layout.prefixCondition(shape);
    return condition.isEmpty() ? countSql : countSql + " WHERE " + condition;
  }

  private byte @Nullable [] read(final KeyType key) {
    final byte[][] value = new byte[1][];
    inTransaction(
        () -> {
          final var keyLength = key.write(keyBuffer, 0);
          final var statement = readStatement(getSql);
          layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
          try (final var result = statement.executeQuery()) {
            if (result.next()) {
              value[0] = result.getBytes(1);
            }
          }
        });
    return value[0];
  }

  private int delete(final KeyType key) throws SQLException {
    context.write();
    final var keyLength = key.write(keyBuffer, 0);
    if (db.preconditionsEnabled()) {
      assertNotAheadOfIteration(key, keyLength);
    }
    final var statement = writeStatement(deleteSql);
    layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
    return statement.executeUpdate();
  }

  /**
   * SQLite skips rows that are deleted ahead of a cursor without any notice, so this turns the
   * silent loss of rows into a failure. Deleting a key that does not exist skips nothing.
   */
  private void assertNotAheadOfIteration(final KeyType key, final int keyLength)
      throws SQLException {
    for (final var cursor : state.cursors()) {
      if (cursor.owner() != context) {
        continue;
      }
      if (cursor.isAhead(keyBuffer.byteArray(), keyLength)) {
        final var statement = readStatement(existsSql);
        layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
        try (final var result = statement.executeQuery()) {
          if (result.next()) {
            throw new IllegalStateException(
                ("Expected to delete key %s of ColumnFamily %s only at or before the position of its running iteration, "
                        + "but it is ahead of it, and SQLite would silently skip rows that are deleted ahead of a cursor")
                    .formatted(key, columnFamily));
          }
        }
        return;
      }
    }
  }

  private byte[] valueBytes(final ValueType value) {
    final var length = value.write(valueBuffer, 0);
    return Arrays.copyOf(valueBuffer.byteArray(), length);
  }

  /** Reads through the writer if the transaction has written, so it sees its own changes. */
  private PreparedStatement readStatement(final String sql) throws SQLException {
    return context.read().prepare(sql);
  }

  /** Takes the write lease if the transaction does not hold it yet. */
  private PreparedStatement writeStatement(final String sql) throws SQLException {
    return context.write().prepare(sql);
  }

  /** Every public operation runs in a transaction, which joins the open one if there is one. */
  private void inTransaction(final SqlOperation operation) {
    context.runInTransaction(
        () -> {
          try {
            if (state.layout() != layout) {
              layout = state.layout();
              buildSql();
            }
            operation.run();
          } catch (final SQLException e) {
            throw SqliteDb.translate(
                "Unexpected error occurred in ColumnFamily %s (table %s)"
                    .formatted(columnFamily, table),
                e);
          }
        });
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    scan(
        null,
        null,
        false,
        true,
        () -> {
          consumer.accept(valueInstance);
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    scan(
        null,
        null,
        false,
        true,
        () -> {
          consumer.accept(keyInstance, valueInstance);
          return true;
        });
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(null, startAtKey, false, true, () -> visitor.visit(keyInstance, valueInstance));
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(null, null, false, true, () -> visitor.visit(keyInstance, valueInstance));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    scan(
        keyPrefix,
        null,
        false,
        true,
        () -> {
          visitor.accept(keyInstance, valueInstance);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(keyPrefix, null, false, true, () -> visitor.visit(keyInstance, valueInstance));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(keyPrefix, startAtKey, false, true, () -> visitor.visit(keyInstance, valueInstance));
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(
        null,
        requireNonNull(startAtKey),
        true,
        true,
        () -> visitor.visit(keyInstance, valueInstance));
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    scan(
        null,
        null,
        false,
        false,
        () -> {
          consumer.accept(keyInstance);
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    scan(null, null, false, false, () -> visitor.visit(keyInstance));
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(null, startAtKey, false, false, () -> visitor.visit(keyInstance));
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    scan(
        keyPrefix,
        null,
        false,
        false,
        () -> {
          visitor.accept(keyInstance);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    scan(keyPrefix, null, false, false, () -> visitor.visit(keyInstance));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(keyPrefix, startAtKey, false, false, () -> visitor.visit(keyInstance));
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(null, requireNonNull(startAtKey), true, false, () -> visitor.visit(keyInstance));
  }

  /**
   * Visits the rows in key order, wrapping the shared key and value instances before each call. The
   * value is neither read nor deserialized if {@code withValue} is false.
   *
   * @param prefix restricts a forward scan to the keys starting with it
   * @param startAt the first key to visit, or if it does not exist the next one in the direction of
   *     the scan
   * @param reverse scans towards smaller keys, starting at or before {@code startAt}
   * @param visitor called with the instances wrapped, returns whether to continue
   */
  private void scan(
      final @Nullable DbKey prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final boolean withValue,
      final RowVisitor visitor) {
    inTransaction(
        () -> {
          if (iterationDepth >= MAX_NESTED_ITERATIONS) {
            throw new IllegalStateException(
                "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
          }
          iterationDepth++;
          try {
            runScan(prefix, startAt, reverse, withValue, visitor);
          } finally {
            iterationDepth--;
          }
        });
  }

  private void runScan(
      final @Nullable DbKey prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final boolean withValue,
      final RowVisitor visitor)
      throws SQLException {
    // the buffers are shared with point operations of the visitor, so copy the keys right away
    byte[] prefixBytes = new byte[0];
    var shape = new PrefixShape(0, 0);
    if (prefix != null) {
      final int length = prefix.write(keyBuffer, 0);
      prefixBytes = Arrays.copyOf(keyBuffer.byteArray(), length);
      shape = layout.prefixShape(prefixBytes, length);
    }
    byte[] startBytes = new byte[0];
    if (startAt != null) {
      final int length = startAt.write(keyBuffer, 0);
      startBytes = Arrays.copyOf(keyBuffer.byteArray(), length);
    }

    boolean hasUpperBound = false;
    if (shape.partialBytes() > 0) {
      final var partial =
          Arrays.copyOfRange(
              prefixBytes, prefixBytes.length - shape.partialBytes(), prefixBytes.length);
      hasUpperBound = KeyLayout.upperBound(partial) != null;
    }
    final var spec = new ScanSpec(shape, hasUpperBound, startAt != null, reverse, withValue);
    final var sql = scanSql.computeIfAbsent(spec, s -> layout.scanSql(table, s));

    final var connection = context.read();
    final var statement = connection.acquireCursorStatement(sql);
    final @Nullable Cursor cursor =
        db.preconditionsEnabled() ? new Cursor(context, reverse, prefixBytes) : null;
    try {
      layout.bindScan(
          statement, spec, prefixBytes, prefixBytes.length, startBytes, startBytes.length);
      if (cursor != null) {
        state.cursors().add(cursor);
      }
      try (final var rows = statement.executeQuery()) {
        final int keyColumns = layout.columnNames().size();
        boolean visitNext = true;
        while (visitNext && rows.next()) {
          final var keyBytes = layout.readKey(rows, 1);
          if (cursor != null) {
            cursor.moveTo(keyBytes);
          }
          keyInstance.wrap(new UnsafeBuffer(keyBytes), 0, keyBytes.length);
          if (withValue) {
            final var valueBytes = rows.getBytes(keyColumns + 1);
            valueInstance.wrap(new UnsafeBuffer(valueBytes), 0, valueBytes.length);
          }
          visitNext = visitor.visit();
        }
      }
    } finally {
      if (cursor != null) {
        state.cursors().remove(cursor);
      }
      connection.releaseCursorStatement(sql, statement);
    }
  }

  @FunctionalInterface
  private interface RowVisitor {
    boolean visit();
  }

  @FunctionalInterface
  private interface SqlOperation {
    void run() throws SQLException;
  }
}
