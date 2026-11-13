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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A follower reading the partition state while the leader owns it, to take snapshots. Waiting for a
 * row lock fails fast here, so a test can show that a read doesn't wait for the leader's writes.
 */
class RdbmsReadOnlyTest {

  private static final int PARTITION = 1;

  private final TestDatabase database = createDatabase(Duration.ofMillis(100));
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
  void shouldNotOpenBeforeThePartitionHasAState() {
    // when
    final var readOnlyDb = factory(1).openReadOnlyDb();

    // then
    assertThat(readOnlyDb).isEmpty();
    assertThat(factory(1).openReadOnlyDb()).isEmpty();
  }

  @Test
  void shouldReadWhatTheLeaderCommits() {
    // given
    final var leader = createDb(1);
    final var leaderColumnFamily = columnFamily(leader, leader.createContext());
    leaderColumnFamily.upsert(key(1), value("before open"));
    final var follower = openReadOnlyDb();
    final var followerColumnFamily = columnFamily(follower, follower.createContext());

    // when
    leaderColumnFamily.upsert(key(2), value("after open"));

    // then
    assertThat(followerColumnFamily.get(key(1))).hasToString("before open");
    assertThat(followerColumnFamily.get(key(2))).hasToString("after open");
  }

  @Test
  void shouldNotWaitForAWriteInProgress() throws Exception {
    // given a write of the leader that reached the database, but isn't committed
    final var leader = createDb(1);
    final var context = leader.createContext();
    final var leaderColumnFamily = columnFamily(leader, context);
    leaderColumnFamily.upsert(key(1), value("committed"));
    final var transaction = context.getCurrentTransaction();
    leaderColumnFamily.upsert(key(1), value("in progress"));
    leaderColumnFamily.count();

    // when
    final var follower = openReadOnlyDb();
    final var value = columnFamily(follower, follower.createContext()).get(key(1));

    // then
    assertThat(value).hasToString("committed");
    transaction.commit();
  }

  @Test
  void shouldLetTheLeaderKeepWriting() {
    // given
    final var leader = createDb(1);
    final var leaderColumnFamily = columnFamily(leader, leader.createContext());
    leaderColumnFamily.upsert(key(1), value("before open"));

    // when
    final var follower = openReadOnlyDb();
    columnFamily(follower, follower.createContext()).get(key(1));
    leaderColumnFamily.upsert(key(2), value("after open"));

    // then
    assertThat(leaderColumnFamily.get(key(2))).hasToString("after open");
  }

  @Test
  void shouldRefuseToWrite() {
    // given
    createDb(1);
    final var follower = openReadOnlyDb();
    final var followerColumnFamily = columnFamily(follower, follower.createContext());

    // when
    assertThatThrownBy(() -> followerColumnFamily.upsert(key(1), value("follower")))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("opened for reading only");
    assertThatThrownBy(() -> followerColumnFamily.deleteIfExists(key(1)))
        .isInstanceOf(UnsupportedOperationException.class);

    // then
    assertThat(followerColumnFamily.exists(key(1))).isFalse();
  }

  @Test
  void shouldTakeASnapshotThatANewLeaderCanOpen() throws Exception {
    // given
    final var snapshot = directory.resolve("snapshot");
    final var runtime = directory.resolve("runtime");
    final var leader = createDb(1);
    columnFamily(leader, leader.createContext()).upsert(key(1), value("old leader"));
    openReadOnlyDb().createSnapshot(snapshot.toFile());
    leader.close();

    // when
    final var factory = factory(2);
    try (final var snapshotOnlyDb = factory.openSnapshotOnlyDb(snapshot.toFile())) {
      snapshotOnlyDb.createSnapshot(runtime.toFile());
    }
    final var newLeader = factory.createDb(runtime.toFile());
    dbs.add(newLeader);

    // then
    assertThat(columnFamily(newLeader, newLeader.createContext()).get(key(1)))
        .hasToString("old leader");
  }

  private RdbmsZeebeDbFactory<TestColumnFamilies> factory(final long term) {
    return new RdbmsZeebeDbFactory<>(
        storage,
        PARTITION,
        () -> term,
        new ConsistencyChecksSettings(true, true),
        SimpleMeterRegistry::new);
  }

  private ZeebeDb<TestColumnFamilies> createDb(final long term) {
    final var db = factory(term).createDb(directory.resolve("leader-" + term).toFile());
    dbs.add(db);
    return db;
  }

  private ZeebeDb<TestColumnFamilies> openReadOnlyDb() {
    final var db = factory(0).openReadOnlyDb().orElseThrow();
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
  TestDatabase createDatabase(final Duration lockTimeout) {
    return new TestH2Database(lockTimeout);
  }
}
