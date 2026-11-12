/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which state a partition gets when it opens a DB without a snapshot. */
class PartitionStatesTest {

  private final TestDatabase database = createDatabase(Duration.ofSeconds(10));
  private final RdbmsStorage storage = RdbmsStorage.open(database.dataSource(), "");
  private final List<ZeebeDb<?>> dbs = Collections.synchronizedList(new ArrayList<>());
  @TempDir private Path directory;

  @AfterEach
  void tearDown() throws Exception {
    for (final var db : dbs) {
      db.close();
    }
    database.close();
  }

  @Test
  void shouldReopenThePartitionsState() {
    // given
    final var first = createDb(1);
    columnFamily(first).upsert(key(1), value("first"));

    // when
    final var second = createDb(1);

    // then
    assertThat(columnFamily(second).get(key(1))).hasToString("first");
  }

  @Test
  void shouldKeepTheStatesOfPartitionsApart() {
    // given
    final var first = createDb(1);
    columnFamily(first).upsert(key(1), value("first"));

    // when
    final var second = createDb(2);

    // then
    assertThat(columnFamily(second).isEmpty()).isTrue();
  }

  @Test
  void shouldStartEmptyWhenThePartitionsStateWasDeleted() throws Exception {
    // given a state whose row was deleted by hand, which leaves its rows behind
    final var deleted = createDb(1);
    columnFamily(deleted).upsert(key(1), value("deleted"));
    final var deletedStateId = stateId(deleted);
    database.execute("DELETE FROM ZS_PARTITION");

    // when
    final var created = createDb(1);

    // then
    assertThat(stateId(created)).isNotEqualTo(deletedStateId);
    assertThat(columnFamily(created).isEmpty()).isTrue();
  }

  @Test
  void shouldGiveEveryPartitionAStateOfItsOwnWhenTheyStartTogether() throws Exception {
    // given
    final int partitionCount = 8;

    // when
    final var started = startTogether(partitionCount, partitionId -> partitionId);

    // then
    for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
      final var db = started.get(partitionId - 1);
      columnFamily(db).upsert(key(1), value("partition " + partitionId));
    }
    for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
      final var columnFamily = columnFamily(started.get(partitionId - 1));
      assertThat(columnFamily.count()).isOne();
      assertThat(columnFamily.get(key(1))).hasToString("partition " + partitionId);
    }
  }

  @Test
  void shouldGiveReplicasOfAPartitionTheSameStateWhenTheyStartTogether() throws Exception {
    // when
    final var started = startTogether(4, replica -> 1);

    // then
    assertThat(started.stream().map(this::stateId).distinct()).hasSize(1);
  }

  private List<ZeebeDb<TestColumnFamilies>> startTogether(
      final int count, final IntUnaryOperator partitionIdOfStarter) throws Exception {
    final var barrier = new CyclicBarrier(count);
    final var executor = Executors.newFixedThreadPool(count);
    try {
      final var started =
          IntStream.rangeClosed(1, count)
              .mapToObj(
                  starter ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            awaitOthers(barrier);
                            return createDb(partitionIdOfStarter.applyAsInt(starter));
                          },
                          executor))
              .toList();
      CompletableFuture.allOf(started.toArray(CompletableFuture[]::new)).get(1, TimeUnit.MINUTES);
      return started.stream().map(CompletableFuture::join).toList();
    } finally {
      executor.shutdownNow();
    }
  }

  private String stateId(final ZeebeDb<?> db) {
    final var snapshot = directory.resolve("snapshot-" + UUID.randomUUID());
    db.createSnapshot(snapshot.toFile());
    return StateMarker.readFrom(snapshot).stateId();
  }

  private ZeebeDb<TestColumnFamilies> createDb(final int partitionId) {
    final var db =
        new RdbmsZeebeDbFactory<TestColumnFamilies>(
                storage,
                partitionId,
                () -> 1,
                new ConsistencyChecksSettings(true, true),
                SimpleMeterRegistry::new)
            .createDb(directory.toFile());
    dbs.add(db);
    return db;
  }

  private static void awaitOthers(final CyclicBarrier barrier) {
    try {
      barrier.await(1, TimeUnit.MINUTES);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static ColumnFamily<DbLong, DbString> columnFamily(final ZeebeDb<TestColumnFamilies> db) {
    return db.createColumnFamily(
        TestColumnFamilies.FIRST, db.createContext(), new DbLong(), new DbString());
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
