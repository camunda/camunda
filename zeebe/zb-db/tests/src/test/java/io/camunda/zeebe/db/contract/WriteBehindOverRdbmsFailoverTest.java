/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import io.camunda.zeebe.db.contract.RdbmsTestFactory.Database;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.rdbms.StateFencedException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The write-behind cache over the shared relational state, across a failover. A snapshot only names
 * the state, so a new leader recovers by opening that state, and a leader that lost its term can't
 * write to it anymore.
 */
class WriteBehindOverRdbmsFailoverTest {

  private static final WriteBehindConfiguration STALLED_WRITER =
      new WriteBehindConfiguration(
          WriteBehindConfiguration.DEFAULT.cacheMaxBytes(),
          WriteBehindConfiguration.DEFAULT.maxPendingBytes(),
          WriteBehindConfiguration.DEFAULT.batchSize(),
          Duration.ofHours(1),
          WriteBehindConfiguration.DEFAULT.snapshotCatchUpTimeout());

  private final int partition = RdbmsTestFactory.nextPartition();
  private final List<ZeebeDb<DefaultColumnFamily>> dbs = new ArrayList<>();
  @TempDir private Path directory;

  @AfterEach
  void tearDown() throws Exception {
    for (final var db : dbs) {
      db.close();
    }
  }

  @Test
  void shouldRecoverEveryTransactionCommittedBeforeTheSnapshot() throws Exception {
    // given - a writer that would otherwise wait an hour before writing anything
    final var snapshot = directory.resolve("snapshot").toFile();
    try (final var oldLeader = factory(1).createDb(directory.resolve("old-runtime").toFile())) {
      final var columnFamily = columnFamily(oldLeader);
      for (long key = 0; key < 100; key++) {
        columnFamily.insert(dbLong(key), dbLong(key * 10));
      }
      oldLeader.createSnapshot(snapshot);
    }

    // when - the new leader recovers as StateControllerImpl does: it copies the snapshot to its
    // runtime directory, then opens the copy
    final var newRuntime = directory.resolve("new-runtime").toFile();
    try (final var snapshotOnlyDb = factory(2).openSnapshotOnlyDb(snapshot)) {
      snapshotOnlyDb.createSnapshot(newRuntime);
    }
    final var newLeader = createDb(2, "new-runtime");

    // then
    assertThat(entries(newLeader)).hasSize(100).containsEntry(99L, 990L);
  }

  @Test
  void shouldStopTheOldLeaderFromWritingOnceANewLeaderClaimedTheState() {
    // given - the old leader's write waits in the cache while the new leader claims the state
    final var oldLeader = createDb(1, "old-runtime");
    columnFamily(oldLeader).insert(dbLong(1), dbLong(10));
    final var newLeader = createDb(2, "new-runtime");

    // when - the snapshot makes the old leader's writer write it
    final var snapshot = directory.resolve("snapshot");
    assertThatThrownBy(() -> oldLeader.createSnapshot(snapshot.toFile()))
        .hasRootCauseInstanceOf(StateFencedException.class);

    // then - no marker, its write never reached the state, and it can't commit anymore
    assertThat(snapshot).doesNotExist();
    assertThat(entries(newLeader)).isEmpty();
    assertThatThrownBy(() -> columnFamily(oldLeader).insert(dbLong(2), dbLong(20)))
        .hasRootCauseInstanceOf(StateFencedException.class);
  }

  /** Overridden to run the same tests on another database. */
  Database database() {
    return Database.H2;
  }

  private ZeebeDbFactory<DefaultColumnFamily> factory(final long term) {
    return WriteBehindOverRdbmsTestFactory.create(
        RdbmsTestFactory.create(database(), partition, () -> term), STALLED_WRITER);
  }

  private ZeebeDb<DefaultColumnFamily> createDb(final long term, final String runtime) {
    final var db = factory(term).createDb(directory.resolve(runtime).toFile());
    dbs.add(db);
    return db;
  }

  private static ColumnFamily<DbLong, DbLong> columnFamily(final ZeebeDb<DefaultColumnFamily> db) {
    return db.createColumnFamily(
        DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
  }

  private static Map<Long, Long> entries(final ZeebeDb<DefaultColumnFamily> db) {
    final Map<Long, Long> entries = new TreeMap<>();
    columnFamily(db).forEach((key, value) -> entries.put(key.getValue(), value.getValue()));
    return entries;
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }
}
