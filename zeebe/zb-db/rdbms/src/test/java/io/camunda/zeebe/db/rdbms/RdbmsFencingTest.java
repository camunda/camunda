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
import io.camunda.zeebe.db.ZeebeDbException;
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
 * Two leaders of different terms on one partition state, as after a failover: only the newer one
 * may write. Waiting for a row lock fails fast here, so a test can show that one side waits for the
 * other without running them at the same time.
 */
class RdbmsFencingTest {

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
  void shouldRejectWritesOfAnOlderTerm() {
    // given
    final var oldLeader = createDb(1);
    final var oldColumnFamily = columnFamily(oldLeader, oldLeader.createContext());
    oldColumnFamily.upsert(key(1), value("before claim"));
    final var newLeader = createDb(2);

    // when
    assertThatThrownBy(() -> oldColumnFamily.upsert(key(2), value("after claim")))
        .isInstanceOf(StateFencedException.class)
        .hasMessageContaining("a leader of term 2 owns it");

    // then
    final var newColumnFamily = columnFamily(newLeader, newLeader.createContext());
    assertThat(newColumnFamily.get(key(1))).hasToString("before claim");
    assertThat(newColumnFamily.exists(key(2))).isFalse();
  }

  @Test
  void shouldNotAttachWithAnOlderTerm() {
    // given
    createDb(2);

    // when / then
    assertThatThrownBy(() -> createDb(1)).isInstanceOf(StateFencedException.class);
  }

  @Test
  void shouldReattachWithTheSameTerm() throws Exception {
    // given
    final var first = createDb(1);
    columnFamily(first, first.createContext()).upsert(key(1), value("first"));
    first.close();

    // when
    final var second = createDb(1);
    final var columnFamily = columnFamily(second, second.createContext());
    columnFamily.upsert(key(2), value("second"));

    // then
    assertThat(columnFamily.get(key(1))).hasToString("first");
    assertThat(columnFamily.get(key(2))).hasToString("second");
  }

  @Test
  void shouldStillReadWithAnOlderTerm() {
    // given
    final var oldLeader = createDb(1);
    final var oldColumnFamily = columnFamily(oldLeader, oldLeader.createContext());
    oldColumnFamily.upsert(key(1), value("old"));

    // when
    createDb(2);

    // then
    assertThat(oldColumnFamily.get(key(1))).hasToString("old");
  }

  @Test
  void shouldDiscardAllWritesOfARejectedTransaction() {
    // given
    final var oldLeader = createDb(1);
    final var context = oldLeader.createContext();
    final var oldColumnFamily = columnFamily(oldLeader, context);
    final var newLeader = createDb(2);

    // when
    assertThatThrownBy(
            () ->
                context.runInTransaction(
                    () -> {
                      oldColumnFamily.upsert(key(1), value("old"));
                      oldColumnFamily.upsert(key(2), value("old"));
                    }))
        .isInstanceOf(StateFencedException.class);

    // then
    assertThat(columnFamily(newLeader, newLeader.createContext()).isEmpty()).isTrue();
  }

  @Test
  void shouldMakeAClaimWaitForAWriteInProgress() throws Exception {
    // given a write of the old term that reached the database, but isn't committed
    final var oldLeader = createDb(1);
    final var context = oldLeader.createContext();
    final var oldColumnFamily = columnFamily(oldLeader, context);
    final var transaction = context.getCurrentTransaction();
    oldColumnFamily.upsert(key(1), value("old"));
    oldColumnFamily.count();

    // when
    assertThatThrownBy(() -> createDb(2)).isInstanceOf(ZeebeDbException.class);
    transaction.commit();
    final var newLeader = createDb(2);

    // then
    assertThat(columnFamily(newLeader, newLeader.createContext()).get(key(1))).hasToString("old");
  }

  @Test
  void shouldRejectAWriteThatWaitedForAClaim() throws Exception {
    // given a newer term's claim that isn't committed yet
    final var oldLeader = createDb(1);
    final var context = oldLeader.createContext();
    final var oldColumnFamily = columnFamily(oldLeader, context);
    try (final var claim = database.dataSource().getConnection();
        final var statement = claim.createStatement()) {
      claim.setAutoCommit(false);
      statement.executeUpdate("UPDATE ZS_PARTITION SET OWNER_TERM = 2");

      // when
      assertThatThrownBy(
              () -> context.runInTransaction(() -> oldColumnFamily.upsert(key(1), value("old"))))
          .isInstanceOf(ZeebeDbException.class);
      claim.commit();
    }

    // then
    assertThatThrownBy(
            () -> context.runInTransaction(() -> oldColumnFamily.upsert(key(1), value("old"))))
        .isInstanceOf(StateFencedException.class);
    assertThat(oldColumnFamily.exists(key(1))).isFalse();
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
