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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which state a partition gets when it opens a DB without a snapshot. */
final class PartitionStatesTest {

  private final TestH2Database database = new TestH2Database(";LOCK_TIMEOUT=10000");
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
    final var partition = new StateIdentity("raft-partition", 1);
    final var first = createDb(partition);
    columnFamily(first).upsert(key(1), value("first"));

    // when
    final var second = createDb(partition);

    // then
    assertThat(columnFamily(second).get(key(1))).hasToString("first");
  }

  @Test
  void shouldKeepTheStatesOfPartitionGroupsApart() {
    // given
    final var first = createDb(new StateIdentity("first-group", 1));
    columnFamily(first).upsert(key(1), value("first"));

    // when
    final var second = createDb(new StateIdentity("second-group", 1));

    // then
    assertThat(columnFamily(second).isEmpty()).isTrue();
  }

  @Test
  void shouldGiveEveryPartitionAStateOfItsOwnWhenTheyStartTogether() throws Exception {
    // given
    final int partitionCount = 8;
    final var barrier = new CyclicBarrier(partitionCount);
    final var executor = Executors.newFixedThreadPool(partitionCount);
    try {
      // when
      final var started =
          IntStream.rangeClosed(1, partitionCount)
              .mapToObj(
                  partitionId ->
                      CompletableFuture.supplyAsync(
                          () -> {
                            awaitOthers(barrier);
                            final var db =
                                createDb(new StateIdentity("raft-partition", partitionId));
                            columnFamily(db).upsert(key(1), value("partition " + partitionId));
                            return db;
                          },
                          executor))
              .toList();
      CompletableFuture.allOf(started.toArray(CompletableFuture[]::new)).get(1, TimeUnit.MINUTES);

      // then
      for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
        final var columnFamily = columnFamily(started.get(partitionId - 1).join());
        assertThat(columnFamily.count()).isOne();
        assertThat(columnFamily.get(key(1))).hasToString("partition " + partitionId);
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private ZeebeDb<TestColumnFamilies> createDb(final StateIdentity partition) {
    final var db =
        new RdbmsZeebeDbFactory<TestColumnFamilies>(
                storage,
                partition,
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
}
