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
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A snapshot is a marker naming the partition state, which stays in the database. These tests go
 * through the steps the broker's recovery takes: open the snapshot, copy it into the runtime
 * directory, then open the DB on that directory.
 */
class RdbmsSnapshotTest {

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
  void shouldOpenTheSameStateFromASnapshot() throws Exception {
    // given
    final var snapshot = directory.resolve("snapshot");
    final var runtime = directory.resolve("runtime");
    final var db = createDb(factory(storage, PARTITION), directory.resolve("first"));
    columnFamily(db, db.createContext()).upsert(key(1), value("before snapshot"));
    db.createSnapshot(snapshot.toFile());
    db.close();

    // when
    final var factory = factory(storage, PARTITION);
    try (final var snapshotOnlyDb = factory.openSnapshotOnlyDb(snapshot.toFile())) {
      snapshotOnlyDb.createSnapshot(runtime.toFile());
    }
    final var recovered = createDb(factory, runtime);

    // then
    assertThat(columnFamily(recovered, recovered.createContext()).get(key(1)))
        .hasToString("before snapshot");
  }

  @Test
  void shouldRejectASnapshotOfAnotherPartition() {
    // given
    final var snapshot = directory.resolve("snapshot");
    createDb(factory(storage, PARTITION), directory.resolve("runtime"))
        .createSnapshot(snapshot.toFile());
    final var otherPartition = factory(storage, 2);

    // when / then
    assertThatThrownBy(() -> otherPartition.openSnapshotOnlyDb(snapshot.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Expected a snapshot of partition 2");
    assertThatThrownBy(() -> otherPartition.createDb(snapshot.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Expected a snapshot of partition 2");
  }

  @Test
  void shouldNotCreateAStateWhenTheDatabaseLacksTheSnapshotsState() throws Exception {
    // given a snapshot of a state in another database
    final var snapshot = directory.resolve("snapshot");
    createDb(factory(storage, PARTITION), directory.resolve("runtime"))
        .createSnapshot(snapshot.toFile());
    try (final var otherDatabase = createDatabase()) {
      final var otherStorage = TestStorage.open(otherDatabase.dataSource(), "");

      // when / then
      assertThatThrownBy(() -> factory(otherStorage, PARTITION).createDb(snapshot.toFile()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("holds none");
      assertThat(otherDatabase.queryLong("SELECT COUNT(*) FROM ZS_PARTITION")).isZero();
    }
  }

  @Test
  void shouldRejectASnapshotOfAnotherStateOfThePartition() throws Exception {
    // given a snapshot of the partition's state in another database, where its state id differs
    final var snapshot = directory.resolve("snapshot");
    createDb(factory(storage, PARTITION), directory.resolve("runtime"))
        .createSnapshot(snapshot.toFile());
    try (final var otherDatabase = createDatabase()) {
      final var otherFactory = factory(TestStorage.open(otherDatabase.dataSource(), ""), PARTITION);
      createDb(otherFactory, directory.resolve("other-runtime"));

      // when / then
      assertThatThrownBy(() -> otherFactory.createDb(snapshot.toFile()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("but it holds the state ");
    }
  }

  @Test
  void shouldRejectADirectoryWithoutSnapshot() {
    // when / then
    assertThatThrownBy(() -> factory(storage, PARTITION).openSnapshotOnlyDb(directory.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(StateMarker.FILE_NAME);
  }

  @Test
  void shouldRejectASnapshotOfAnUnknownVersion() throws Exception {
    // given
    final var snapshot = directory.resolve("snapshot");
    createDb(factory(storage, PARTITION), directory.resolve("runtime"))
        .createSnapshot(snapshot.toFile());
    final var marker = snapshot.resolve(StateMarker.FILE_NAME);
    Files.writeString(marker, Files.readString(marker).replace("version=2", "version=3"));

    // when / then
    assertThatThrownBy(() -> factory(storage, PARTITION).openSnapshotOnlyDb(snapshot.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("version 3");
  }

  @Test
  void shouldOnlyTakeSnapshotsWithASnapshotOnlyDb() {
    // given
    final var snapshot = directory.resolve("snapshot");
    createDb(factory(storage, PARTITION), directory.resolve("runtime"))
        .createSnapshot(snapshot.toFile());

    // when
    final var snapshotOnlyDb = factory(storage, PARTITION).openSnapshotOnlyDb(snapshot.toFile());

    // then
    assertThatThrownBy(snapshotOnlyDb::createContext)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static RdbmsZeebeDbFactory<TestColumnFamilies> factory(
      final RdbmsStorage storage, final int partition) {
    return new RdbmsZeebeDbFactory<>(
        storage,
        partition,
        () -> 1,
        new ConsistencyChecksSettings(true, true),
        SimpleMeterRegistry::new);
  }

  private ZeebeDb<TestColumnFamilies> createDb(
      final RdbmsZeebeDbFactory<TestColumnFamilies> factory, final Path runtime) {
    final var db = factory.createDb(runtime.toFile());
    dbs.add(db);
    return db;
  }

  private static ColumnFamily<DbLong, DbString> columnFamily(
      final ZeebeDb<TestColumnFamilies> db, final TransactionContext context) {
    return db.createColumnFamily(TestColumnFamilies.FIRST, context, new DbLong(), new DbString());
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
