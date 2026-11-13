/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.RawBatchWriter;
import io.camunda.zeebe.db.RawWrite;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.util.buffer.BufferWriter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The write-behind cache's batches, written with their positions. */
class RdbmsBatchWriteTest {

  private static final int PARTITION = 1;

  private final TestDatabase database = createDatabase();
  private final RdbmsStorage storage = TestStorage.open(database.dataSource(), "");
  private final List<ZeebeDb<?>> dbs = new ArrayList<>();
  @TempDir private Path directory;

  @AfterEach
  void tearDown() throws Exception {
    for (final var db : dbs) {
      db.close();
    }
    database.close();
  }

  @Test
  void shouldWriteABatch() {
    // given
    final var db = createDb(1);
    final var first = columnFamily(db, TestColumnFamilies.FIRST);
    first.insert(key(1), value("deleted"));
    first.insert(key(2), value("old"));

    // when
    batchWriter(db)
        .write(
            List.of(
                write(TestColumnFamilies.FIRST, 1, null, 3),
                write(TestColumnFamilies.FIRST, 2, "updated", 3),
                write(TestColumnFamilies.FIRST, 3, "inserted", 4),
                write(TestColumnFamilies.SECOND, 1, "other table", 4)),
            4);

    // then
    assertThat(first.exists(key(1))).isFalse();
    assertThat(first.get(key(2))).hasToString("updated");
    assertThat(first.get(key(3))).hasToString("inserted");
    assertThat(columnFamily(db, TestColumnFamilies.SECOND).get(key(1))).hasToString("other table");
  }

  @Test
  void shouldRecordThePositions() throws SQLException {
    // given
    final var db = createDb(1);
    batchWriter(db)
        .write(
            List.of(
                write(TestColumnFamilies.FIRST, 1, "a", 3),
                write(TestColumnFamilies.FIRST, 2, "b", 3)),
            3);

    // when
    batchWriter(db).write(List.of(write(TestColumnFamilies.FIRST, 2, "c", 8)), 9);

    // then the first key keeps its position, the second gets the newer one
    assertThat(database.queryLong("SELECT MIN(UPDATED_POSITION) FROM ZS_DEFAULT")).isEqualTo(3);
    assertThat(database.queryLong("SELECT MAX(UPDATED_POSITION) FROM ZS_DEFAULT")).isEqualTo(8);
    assertThat(writtenPosition()).isEqualTo(9);
  }

  @Test
  void shouldRejectABatchOfAnOlderTerm() throws SQLException {
    // given
    final var oldLeader = createDb(1);
    final var newLeader = createDb(2);

    // when
    assertThatThrownBy(
            () ->
                batchWriter(oldLeader)
                    .write(List.of(write(TestColumnFamilies.FIRST, 1, "old", 3)), 3))
        .isInstanceOf(StateFencedException.class);

    // then
    assertThat(columnFamily(newLeader, TestColumnFamilies.FIRST).isEmpty()).isTrue();
    assertThat(writtenPosition()).isEqualTo(StateMapper.NO_POSITION);
  }

  private long writtenPosition() throws SQLException {
    return database.queryLong("SELECT WRITTEN_POSITION FROM ZS_PARTITION");
  }

  private ZeebeDb<TestColumnFamilies> createDb(final long term) {
    final var db =
        new RdbmsZeebeDbFactory<TestColumnFamilies>(
                storage,
                PARTITION,
                () -> term,
                new ConsistencyChecksSettings(true, true),
                SimpleMeterRegistry::new)
            .createDb(directory.toFile());
    dbs.add(db);
    return db;
  }

  private static RawBatchWriter batchWriter(final ZeebeDb<TestColumnFamilies> db) {
    return (RawBatchWriter) db;
  }

  private static ColumnFamily<DbLong, DbString> columnFamily(
      final ZeebeDb<TestColumnFamilies> db, final TestColumnFamilies columnFamily) {
    return db.createColumnFamily(columnFamily, db.createContext(), new DbLong(), new DbString());
  }

  private static RawWrite write(
      final TestColumnFamilies columnFamily,
      final long key,
      final @Nullable String value,
      final long position) {
    return new RawWrite(
        columnFamily, bytes(key(key)), value == null ? null : bytes(value(value)), position);
  }

  private static byte[] bytes(final BufferWriter writer) {
    final var bytes = new byte[writer.getLength()];
    writer.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }

  private static DbLong key(final long value) {
    final var key = new DbLong();
    key.wrapLong(value);
    return key;
  }

  private static DbString value(final String value) {
    final var dbString = new DbString();
    dbString.wrapString(value);
    return dbString;
  }

  /** Overridden to run the same tests on another database. */
  TestDatabase createDatabase() {
    return new TestH2Database();
  }
}
