/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import io.camunda.zeebe.db.SnapshotCopy;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies the column families of a scope from one SQLite snapshot into a new one.
 *
 * <p>A column family is a table, so the copy attaches the source snapshot read-only to a new
 * database and, for every in-scope column family whose table exists in the source, runs the table's
 * own {@code CREATE} statement from the source and {@code INSERT ... SELECT}s its rows. Taking the
 * DDL from the source rather than deriving it keeps the key layout exactly as it was written,
 * including the opaque {@code k BLOB PRIMARY KEY} fallback, which cannot be derived because it
 * depends on the key shapes the column family has been used with.
 *
 * <p>The target holds the tables of the requested scopes only. Tables are created lazily on first
 * use, and a missing table reads as an empty column family, so a column family that is out of scope
 * (or in scope but never used) is absent. That is what RocksDB's result looks like observably, an
 * out-of-scope column family has no keys.
 */
public final class SqliteSnapshotCopy implements SnapshotCopy {

  private static final Logger LOG = LoggerFactory.getLogger(SqliteSnapshotCopy.class);
  private static final String SOURCE = "snapshot_source";

  @Override
  public void copySnapshot(
      final Path fromPath, final Path toPath, final Set<ColumnFamilyScope> scopes) {
    // fails with a clear message if the source is not a readable SQLite snapshot
    SqliteSnapshotOnlyDb.open(fromPath); // holds no resources, so there is nothing to close
    final var source = fromPath.resolve(SqliteDb.DB_FILE_NAME);

    try {
      Files.createDirectories(toPath);
      try (final var target = SqliteConnection.openWriter(toPath.resolve(SqliteDb.DB_FILE_NAME))) {
        // The snapshot store checksums this file and persists the checksum with an fsync, but only
        // flushes the directory of the file itself. The writer runs with synchronous=OFF, so make
        // the copy durable the way VACUUM INTO does for a regular snapshot.
        target.execute("PRAGMA synchronous=FULL");
        // mode=ro keeps the source from being modified, not even a journal is created next to it
        target.execute("ATTACH DATABASE '%s?mode=ro' AS %s".formatted(sqlUri(source), SOURCE));
        target.execute("BEGIN");
        try {
          for (final var columnFamily : ZbColumnFamilies.values()) {
            if (scopes.contains(columnFamily.partitionScope())) {
              copyTable(target, SqliteDb.tableName(columnFamily), columnFamily);
            }
          }
          target.execute("COMMIT");
        } catch (final SQLException | RuntimeException e) {
          target.execute("ROLLBACK");
          throw e;
        }
        target.execute("DETACH DATABASE " + SOURCE);
      }
    } catch (final SQLException | IOException e) {
      throw new ZeebeDbException(
          "Failed to copy the SQLite snapshot '%s' to '%s' for the scopes %s"
              .formatted(fromPath, toPath, scopes),
          e);
    }
  }

  private void copyTable(
      final SqliteConnection target, final String table, final ZbColumnFamilies columnFamily)
      throws SQLException {
    final List<String> ddl = new ArrayList<>();
    // The table and its indexes are copied, triggers and views are not: a trigger would run against
    // the target, and one that is attached to the table would be created before the table exists.
    // The table sorts first, so it exists when its indexes are created. Automatic indexes have no
    // SQL and are recreated with the table.
    try (final var statement =
        target
            .jdbc()
            .prepareStatement(
                """
                    SELECT sql FROM %s.sqlite_master
                    WHERE tbl_name = ? AND type IN ('table', 'index') AND sql IS NOT NULL
                    ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name
                    """
                    .formatted(SOURCE))) {
      statement.setString(1, table);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          ddl.add(result.getString(1));
        }
      }
    }
    if (ddl.isEmpty()) {
      return; // never used, so there is nothing to copy
    }

    LOG.debug("Copying column family '{}'", columnFamily);
    for (final var statement : ddl) {
      target.execute(statement);
    }
    target.execute("INSERT INTO main.%1$s SELECT * FROM %2$s.%1$s".formatted(table, SOURCE));
  }

  private static String sqlUri(final Path file) {
    return file.toAbsolutePath().toUri().toString().replace("'", "''");
  }
}
