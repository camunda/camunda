/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.sqlite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dynamic scaling copies the column families of one scope from a snapshot into a new snapshot. The
 * result must hold the tables of that scope with their rows and nothing else, and must be a
 * snapshot that opens like any other.
 */
final class SqliteSnapshotCopyTest {

  private static final ZbColumnFamilies GLOBAL_CF = ZbColumnFamilies.DEPRECATED_PROCESS_VERSION;
  private static final ZbColumnFamilies LOCAL_CF = ZbColumnFamilies.VARIABLES;
  private static final ZbColumnFamilies OPAQUE_LOCAL_CF = ZbColumnFamilies.KEY;
  private static final ZbColumnFamilies OPAQUE_GLOBAL_CF =
      ZbColumnFamilies.DEPRECATED_PROCESS_CACHE;

  @TempDir Path scratch;

  private final SqliteSnapshotCopy copy = new SqliteSnapshotCopy();
  private Path source;

  @BeforeEach
  void createSource() throws Exception {
    assertThat(GLOBAL_CF.partitionScope()).isEqualTo(ColumnFamilyScope.GLOBAL);
    assertThat(OPAQUE_GLOBAL_CF.partitionScope()).isEqualTo(ColumnFamilyScope.GLOBAL);
    assertThat(LOCAL_CF.partitionScope()).isEqualTo(ColumnFamilyScope.PARTITION_LOCAL);
    assertThat(OPAQUE_LOCAL_CF.partitionScope()).isEqualTo(ColumnFamilyScope.PARTITION_LOCAL);

    source = scratch.resolve("source");
    try (final var db =
        new SqliteDbFactory<ZbColumnFamilies>().createDb(scratch.resolve("rt").toFile())) {
      final var context = db.createContext();
      final var global = longs(db, GLOBAL_CF);
      global.insert(dbLong(1), dbLong(10));
      global.insert(dbLong(2), dbLong(20));

      final var local = longs(db, LOCAL_CF);
      local.insert(dbLong(7), dbLong(70));

      // used with two key shapes, so the table falls back to the opaque key column
      for (final var cf : List.of(OPAQUE_GLOBAL_CF, OPAQUE_LOCAL_CF)) {
        final var legacy = longs(db, cf);
        legacy.insert(dbLong(3), dbLong(30));
        legacy.insert(dbLong(-4), dbLong(-40));
        db.createColumnFamily(
            cf, context, new DbForeignKey<>(new DbLong(), ZbColumnFamilies.DEFAULT), new DbLong());
      }
      db.createSnapshot(source.toFile());
    }
  }

  @Test
  void shouldCopyOnlyGlobalTables() throws Exception {
    // when
    final var target = scratch.resolve("global");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.GLOBAL));

    // then
    assertThat(tables(target))
        .containsOnlyKeys(SqliteDb.tableName(GLOBAL_CF), SqliteDb.tableName(OPAQUE_GLOBAL_CF));
    assertThat(rowCount(target, GLOBAL_CF)).isEqualTo(2);
    assertThat(rowCount(target, OPAQUE_GLOBAL_CF)).isEqualTo(2);
  }

  @Test
  void shouldCopyOnlyPartitionLocalTables() throws Exception {
    // when
    final var target = scratch.resolve("local");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.PARTITION_LOCAL));

    // then
    assertThat(tables(target))
        .containsOnlyKeys(SqliteDb.tableName(LOCAL_CF), SqliteDb.tableName(OPAQUE_LOCAL_CF));
    assertThat(rowCount(target, LOCAL_CF)).isEqualTo(1);
    assertThat(rowCount(target, OPAQUE_LOCAL_CF)).isEqualTo(2);
  }

  @Test
  void shouldCopyEverythingForAllScopes() throws Exception {
    // when
    final var target = scratch.resolve("all");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.values()));

    // then
    assertThat(tables(target)).isEqualTo(tables(source));
  }

  @Test
  void shouldCopyNothingForNoScope() throws Exception {
    // when
    final var target = scratch.resolve("none");
    copy.copySnapshot(source, target, Set.of());

    // then
    assertThat(tables(target)).isEmpty();
  }

  @Test
  void shouldPreserveSchemaIncludingOpaqueFallback() throws Exception {
    // when
    final var target = scratch.resolve("local");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.PARTITION_LOCAL));

    // then
    final var sourceTables = tables(source);
    final var targetTables = tables(target);
    for (final var cf : List.of(LOCAL_CF, OPAQUE_LOCAL_CF)) {
      final var name = SqliteDb.tableName(cf);
      assertThat(targetTables.get(name)).isEqualTo(sourceTables.get(name));
    }
    assertThat(targetTables.get(SqliteDb.tableName(OPAQUE_LOCAL_CF)))
        .contains("k BLOB NOT NULL", "PRIMARY KEY (k)");
  }

  @Test
  void shouldOpenCopyLikeAnySnapshotAndKeepRowsReadable() throws Exception {
    // given
    final var target = scratch.resolve("local");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.PARTITION_LOCAL));

    // when
    try (final var db = new SqliteDbFactory<ZbColumnFamilies>().createDb(target.toFile())) {
      // then the opaque table still serves both key shapes, ordered by the serialized key
      final var legacy = longs(db, OPAQUE_LOCAL_CF);
      assertThat(legacy.get(dbLong(3)).getValue()).isEqualTo(30);
      assertThat(legacy.get(dbLong(-4)).getValue()).isEqualTo(-40);
      final List<Long> keys = new ArrayList<>();
      legacy.forEachKey(key -> keys.add(key.getValue()));
      assertThat(keys).containsExactly(3L, -4L);
      assertThat(longs(db, LOCAL_CF).get(dbLong(7)).getValue()).isEqualTo(70);

      // and the column families of the other scope are empty
      assertThat(longs(db, GLOBAL_CF).isEmpty()).isTrue();
    }
  }

  @Test
  void shouldLeaveSourceUntouchedAndWriteNoSideFiles() throws Exception {
    // given
    final var before = Files.readAllBytes(source.resolve(SqliteDb.DB_FILE_NAME));

    // when
    final var target = scratch.resolve("all");
    copy.copySnapshot(source, target, Set.of(ColumnFamilyScope.values()));

    // then
    assertThat(Files.readAllBytes(source.resolve(SqliteDb.DB_FILE_NAME))).isEqualTo(before);
    try (final Stream<Path> files = Files.list(source)) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly(SqliteDb.DB_FILE_NAME);
    }
    try (final Stream<Path> files = Files.list(target)) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly(SqliteDb.DB_FILE_NAME);
    }
  }

  @Test
  void shouldRejectSourceWithoutDatabase() throws Exception {
    // given
    final var empty = Files.createDirectories(scratch.resolve("empty"));

    // then
    assertThatThrownBy(
            () ->
                copy.copySnapshot(
                    empty, scratch.resolve("target"), Set.of(ColumnFamilyScope.GLOBAL)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(SqliteDb.DB_FILE_NAME);
  }

  @Test
  void shouldBeUsedThroughFactoryCapabilities() {
    assertThat(new SqliteDbFactory<ZbColumnFamilies>().capabilities().scopedCopy()).isTrue();
  }

  private static DbLong dbLong(final long value) {
    final var result = new DbLong();
    result.wrapLong(value);
    return result;
  }

  private static ColumnFamily<DbLong, DbLong> longs(
      final ZeebeDb<ZbColumnFamilies> db, final ZbColumnFamilies cf) {
    return db.createColumnFamily(cf, db.createContext(), new DbLong(), new DbLong());
  }

  /** The CREATE statement of every table in the snapshot, by table name. */
  private static Map<String, String> tables(final Path snapshot) throws Exception {
    final Map<String, String> tables = new LinkedHashMap<>();
    final var file = new File(snapshot.toFile(), SqliteDb.DB_FILE_NAME);
    try (final var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery(
                "SELECT name, sql FROM sqlite_master WHERE type = 'table' ORDER BY name")) {
      while (result.next()) {
        tables.put(result.getString(1), result.getString(2));
      }
    }
    return tables;
  }

  private static int rowCount(final Path snapshot, final ZbColumnFamilies cf) throws Exception {
    final var file = new File(snapshot.toFile(), SqliteDb.DB_FILE_NAME);
    try (final var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        final var statement = connection.createStatement();
        final var result =
            statement.executeQuery("SELECT count(*) FROM " + SqliteDb.tableName(cf))) {
      return result.getInt(1);
    }
  }
}
