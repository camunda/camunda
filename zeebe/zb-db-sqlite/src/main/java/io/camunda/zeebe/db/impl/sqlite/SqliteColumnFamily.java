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
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.sqlite.KeyLayout.PrefixShape;
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
 * Point operations and counting on the table of one column family. Iteration is not implemented
 * yet, those methods throw {@link UnsupportedOperationException}.
 *
 * <p>Prepared statements are created on first use and cached by the database.
 */
final class SqliteColumnFamily<
        ColumnFamilyType extends Enum<?> & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private static final String ITERATION_NOT_IMPLEMENTED =
      "Iteration is not implemented yet, milestone M5 implements it";

  private final SqliteDb<?> db;
  private final ColumnFamilyType columnFamily;
  private final String table;
  private final KeyLayout layout;
  private final TransactionContext context;
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final String insertSql;
  private final String updateSql;
  private final String upsertSql;
  private final String getSql;
  private final String deleteSql;
  private final String existsSql;
  private final String isEmptySql;
  private final String countSql;
  private final Map<PrefixShape, String> countPrefixSql = new HashMap<>();

  SqliteColumnFamily(
      final SqliteDb<?> db,
      final ColumnFamilyType columnFamily,
      final String table,
      final KeyLayout layout,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.db = db;
    this.columnFamily = columnFamily;
    this.table = table;
    this.layout = layout;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;

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
          final var statement = statement(insertSql);
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
          final var statement = statement(updateSql);
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
          final var statement = statement(upsertSql);
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
          final var statement = statement(existsSql);
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
          try (final var result = statement(isEmptySql).executeQuery()) {
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
          try (final var result = statement(countSql).executeQuery()) {
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
          final var statement = statement(sql);
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
          final var statement = statement(getSql);
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
    final var keyLength = key.write(keyBuffer, 0);
    final var statement = statement(deleteSql);
    layout.bindKey(statement, 1, keyBuffer.byteArray(), keyLength);
    return statement.executeUpdate();
  }

  private byte[] valueBytes(final ValueType value) {
    final var length = value.write(valueBuffer, 0);
    return Arrays.copyOf(valueBuffer.byteArray(), length);
  }

  private PreparedStatement statement(final String sql) throws SQLException {
    return db.prepare(sql);
  }

  /** Every public operation runs in a transaction, which joins the open one if there is one. */
  private void inTransaction(final SqlOperation operation) {
    context.runInTransaction(
        () -> {
          try {
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
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw new UnsupportedOperationException(ITERATION_NOT_IMPLEMENTED);
  }

  @FunctionalInterface
  private interface SqlOperation {
    void run() throws SQLException;
  }
}
