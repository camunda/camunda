/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.sqlite.KeyLayout.OutOfDomainKeyException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import org.agrona.ExpandableArrayBuffer;

/**
 * Checks that the foreign keys of a key or value exist in the column family they reference, like
 * RocksDB's {@code ForeignKeyChecker}: a {@link DbForeignKey.MatchType#Full} foreign key must exist
 * as a key, a {@link DbForeignKey.MatchType#Prefix} one as the prefix of a key, and a foreign key
 * whose {@code skip} predicate holds is not checked.
 *
 * <p>The referenced table is looked up by its column family, so the check works on any column
 * family and uses the layout of the referenced table, not that of the one that is written to. The
 * check is enabled in tests only, no instance is created otherwise.
 *
 * <p>One instance is used by one context, which is used by one thread at a time.
 */
final class SqliteForeignKeyChecker {

  private final SqliteDb<?> db;
  private final SqliteTransactionContext context;
  private final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();

  SqliteForeignKeyChecker(final SqliteDb<?> db, final SqliteTransactionContext context) {
    this.db = db;
    this.context = context;
  }

  /**
   * @throws ZeebeDbInconsistentException if a foreign key of the object does not exist
   */
  void assertExists(final Object keyOrValue) throws SQLException {
    if (keyOrValue instanceof final ContainsForeignKeys containsForeignKeys) {
      for (final var foreignKey : containsForeignKeys.containedForeignKeys()) {
        assertExists(foreignKey);
      }
    }
  }

  private void assertExists(final DbForeignKey<DbKey> foreignKey) throws SQLException {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }

    final int length = foreignKey.write(buffer, 0);
    final var bytes = Arrays.copyOf(buffer.byteArray(), length);
    final var exists =
        switch (foreignKey.match()) {
          case Full -> exists(foreignKey, bytes, false);
          case Prefix -> exists(foreignKey, bytes, true);
        };
    if (!exists) {
      throw new ZeebeDbInconsistentException(
          "Foreign key "
              + foreignKey.inner()
              + " does not exist"
              + (foreignKey.match() == DbForeignKey.MatchType.Prefix ? " as prefix" : "")
              + " in "
              + foreignKey.columnFamily());
    }
  }

  private boolean exists(
      final DbForeignKey<DbKey> foreignKey, final byte[] key, final boolean prefix)
      throws SQLException {
    final var table = SqliteDb.tableName(foreignKey.columnFamily());
    final var state = db.tableState(table);
    if (state == null) {
      // the referenced column family was never used, so it holds nothing
      return false;
    }

    final var layout = state.layout();
    final String condition;
    final KeyBinder binder;
    if (prefix) {
      final var shape = layout.prefixShape(key, key.length);
      condition = layout.prefixCondition(shape);
      binder = statement -> layout.bindPrefix(statement, shape, key, key.length);
    } else {
      condition = String.join(" AND ", layout.columnNames().stream().map(c -> c + " = ?").toList());
      binder = statement -> layout.bindKey(statement, 1, key, key.length);
    }
    final var sql =
        "SELECT 1 FROM " + table + (condition.isEmpty() ? "" : " WHERE " + condition) + " LIMIT 1";

    final var statement = context.read().prepare(sql);
    try {
      binder.bind(statement);
    } catch (final OutOfDomainKeyException e) {
      // no row can have a key outside of its domain
      return false;
    }
    try (final var result = statement.executeQuery()) {
      return result.next();
    }
  }

  @FunctionalInterface
  private interface KeyBinder {
    void bind(PreparedStatement statement) throws SQLException;
  }
}
