/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LayeredZeebeDbTest {

  private final ZeebeDbFactory<ColumnFamilies> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @TempDir private File dbDirectory;
  @TempDir private File snapshotParentDirectory;

  private ZeebeDb<ColumnFamilies> inner;
  private LayeredZeebeDb<ColumnFamilies> layered;
  private TransactionContext layeredContext;

  private ColumnFamily<DbLong, DbLong> oneColumnFamily;
  private ColumnFamily<DbString, DbLong> positionColumnFamily;
  private DbLong oneKey;
  private DbLong oneValue;
  private DbString positionKey;
  private DbLong positionValue;

  @BeforeEach
  void setUp() {
    inner = dbFactory.createDb(dbDirectory);
    layered = new LayeredZeebeDb<>(inner, LayeredZeebeDbConfig.defaults());
    layeredContext = layered.layeredContext();

    oneKey = new DbLong();
    oneValue = new DbLong();
    oneColumnFamily =
        layered.createColumnFamily(ColumnFamilies.ONE, layeredContext, oneKey, oneValue);

    positionKey = new DbString();
    positionValue = new DbLong();
    positionColumnFamily =
        layered.createColumnFamily(
            ColumnFamilies.DEFAULT, layeredContext, positionKey, positionValue);
  }

  @AfterEach
  void tearDown() {
    if (layered != null) {
      CloseHelper.quietClose(layered);
      layered = null;
      inner = null;
    }
  }

  // ------------------------------------------------------------------
  // Transaction parity on the layered context
  // ------------------------------------------------------------------

  @Test
  void shouldReturnSameLayeredContextOnEveryCall() {
    // when
    final TransactionContext first = layered.layeredContext();
    final TransactionContext second = layered.layeredContext();

    // then
    assertThat(first).isSameAs(second);
  }

  @Test
  void shouldCommitExplicitTransactionAndReadBack() throws Exception {
    // given
    final ZeebeDbTransaction transaction = layeredContext.getCurrentTransaction();
    transaction.run(() -> upsertOne(1, -1));

    // when
    transaction.commit();

    // then
    assertThat(getOne(1)).isEqualTo(-1);
  }

  @Test
  void shouldReadUncommittedWritesInOpenTransaction() throws Exception {
    // given
    final ZeebeDbTransaction transaction = layeredContext.getCurrentTransaction();

    // when
    transaction.run(() -> upsertOne(1, -1));

    // then no commit, but the open transaction sees its own writes
    assertThat(getOne(1)).isEqualTo(-1);
  }

  @Test
  void shouldRollbackStagedWrites() throws Exception {
    // given
    final ZeebeDbTransaction transaction = layeredContext.getCurrentTransaction();
    transaction.run(() -> upsertOne(1, -1));

    // when
    transaction.rollback();

    // then
    oneKey.wrapLong(1);
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldPreservePriorCommitsOnRollback() throws Exception {
    // given a committed value
    layeredContext.runInTransaction(() -> upsertOne(1, 100));

    // when a later batch overwrites it and adds a key, but rolls back
    final ZeebeDbTransaction transaction = layeredContext.getCurrentTransaction();
    transaction.run(
        () -> {
          upsertOne(1, 200);
          upsertOne(2, 300);
        });
    transaction.rollback();

    // then only the staged batch is gone, the prior commit is untouched
    assertThat(getOne(1)).isEqualTo(100);
    oneKey.wrapLong(2);
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldCommitRunInTransactionOnSuccess() {
    // when
    layeredContext.runInTransaction(
        () -> {
          upsertOne(1, -1);
          upsertOne(2, -2);
        });

    // then
    assertThat(getOne(1)).isEqualTo(-1);
    assertThat(getOne(2)).isEqualTo(-2);
  }

  @Test
  void shouldRollbackRunInTransactionOnErrorAndNotWrapRuntimeExceptions() {
    // given
    final RuntimeException expected = new RuntimeException("expected");

    // when
    assertThatThrownBy(
            () ->
                layeredContext.runInTransaction(
                    () -> {
                      upsertOne(1, -1);
                      throw expected;
                    }))
        .isSameAs(expected);

    // then
    oneKey.wrapLong(1);
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldReuseOpenTransactionWhenNesting() {
    // given
    layeredContext.runInTransaction(() -> upsertOne(1, -1));
    final Map<Long, Long> readWithinOuter = new HashMap<>();

    // when the inner call joins the outer transaction and the outer call reads its effect
    layeredContext.runInTransaction(
        () -> {
          layeredContext.runInTransaction(() -> upsertOne(1, 42));
          readWithinOuter.put(1L, getOne(1));
        });

    // then
    assertThat(readWithinOuter).containsEntry(1L, 42L);
    assertThat(getOne(1)).isEqualTo(42);
  }

  @Test
  void shouldReturnSameTransactionWhileOpen() throws Exception {
    // given
    final ZeebeDbTransaction transaction = layeredContext.getCurrentTransaction();

    // when / then
    transaction.run(() -> assertThat(layeredContext.getCurrentTransaction()).isSameAs(transaction));
  }

  @Test
  void shouldCommitStandaloneOperationImmediately() {
    // given a write outside any explicit transaction
    upsertOne(1, -1);

    // when a later transaction fails and rolls back
    assertThatThrownBy(
            () ->
                layeredContext.runInTransaction(
                    () -> {
                      upsertOne(2, -2);
                      throw new RuntimeException("expected");
                    }))
        .isInstanceOf(RuntimeException.class);

    // then the standalone write was committed on its own, like the reference implementation
    assertThat(getOne(1)).isEqualTo(-1);
    oneKey.wrapLong(2);
    assertThat(oneColumnFamily.exists(oneKey)).isFalse();
  }

  @Test
  void shouldIterateOverStagedAndCommittedState() {
    // given committed state
    layeredContext.runInTransaction(
        () -> {
          upsertOne(1, -1);
          upsertOne(2, -2);
        });

    // when a transaction updates and inserts, then iterates
    final Map<Long, Long> visited = new HashMap<>();
    layeredContext.runInTransaction(
        () -> {
          upsertOne(2, -5);
          upsertOne(3, -3);
          oneColumnFamily.forEach((key, value) -> visited.put(key.getValue(), value.getValue()));
        });

    // then the iteration merged staged writes over committed state
    assertThat(visited).isEqualTo(Map.of(1L, -1L, 2L, -5L, 3L, -3L));
  }

  // ------------------------------------------------------------------
  // Isolation between the layered path and pass-through consumers
  // ------------------------------------------------------------------

  @Test
  void shouldNotLeakLayeredCommitsBeforePersistRound() {
    // given a committed layered write
    layeredContext.runInTransaction(() -> upsertOne(1, -1));

    // when reading through a pass-through context
    final TransactionContext passThroughContext = layered.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> passThroughColumnFamily =
        layered.createColumnFamily(ColumnFamilies.ONE, passThroughContext, key, value);
    key.wrapLong(1);

    // then the write is buffered in memory only — RocksDB does not hold it yet
    assertThat(passThroughColumnFamily.exists(key)).isFalse();
    assertThat(getOne(1)).isEqualTo(-1);
  }

  @Test
  void shouldServePassThroughWritesToLayeredReads() {
    // given an exporter-style writer with its own context and transaction
    final TransactionContext exporterContext = layered.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> exporterColumnFamily =
        layered.createColumnFamily(ColumnFamilies.ONE, exporterContext, key, value);

    // when it commits a write through its own transaction
    exporterContext.runInTransaction(
        () -> {
          key.wrapLong(7);
          value.wrapLong(77);
          exporterColumnFamily.upsert(key, value);
        });

    // then the layered path reads it through its delegate
    assertThat(getOne(7)).isEqualTo(77);
  }

  @Test
  void shouldReflectBufferedWritesInIsEmpty() {
    // given a committed layered write that was not persisted yet
    layeredContext.runInTransaction(() -> upsertOne(1, -1));

    // when / then
    assertThat(layered.isEmpty(ColumnFamilies.ONE, layeredContext)).isFalse();
    assertThat(layered.isEmpty(ColumnFamilies.ONE, layered.createContext())).isTrue();
  }

  // ------------------------------------------------------------------
  // Persist rounds and recovery
  // ------------------------------------------------------------------

  @Test
  void shouldPersistBufferedStateAtomicallyInRound() throws Exception {
    // given typed writes across several commits, including a position-style key
    layeredContext.runInTransaction(() -> upsertOne(1, 100));
    layeredContext.runInTransaction(() -> upsertOne(2, 200));
    layeredContext.runInTransaction(
        () -> {
          upsertOne(2, 201);
          upsertPosition(42);
        });

    // when a full persist round runs
    final var coordinator = layered.defaultDomain().coordinator();
    final var round = coordinator.prepareRound(42);
    round.persist();
    coordinator.completeRound(round, true);

    // then the same keys are readable from RocksDB through a plain pass-through context
    final TransactionContext passThroughContext = layered.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> passThroughOne =
        layered.createColumnFamily(ColumnFamilies.ONE, passThroughContext, key, value);
    key.wrapLong(1);
    assertThat(passThroughOne.get(key).getValue()).isEqualTo(100);
    key.wrapLong(2);
    assertThat(passThroughOne.get(key).getValue()).isEqualTo(201);

    // and the position key landed in the same atomic round
    final DbString posKey = new DbString();
    final DbLong posValue = new DbLong();
    final ColumnFamily<DbString, DbLong> passThroughPosition =
        layered.createColumnFamily(ColumnFamilies.DEFAULT, passThroughContext, posKey, posValue);
    posKey.wrapString("lastProcessedPosition");
    assertThat(passThroughPosition.get(posKey).getValue()).isEqualTo(42);
  }

  @Test
  void shouldRecoverPersistedStateAfterReopen() throws Exception {
    // given a persisted round
    layeredContext.runInTransaction(
        () -> {
          upsertOne(1, 100);
          upsertPosition(42);
        });
    final var coordinator = layered.defaultDomain().coordinator();
    final var round = coordinator.prepareRound(42);
    round.persist();
    coordinator.completeRound(round, true);

    // when everything is closed and the database is reopened from a snapshot — the production
    // recovery path; the database discards unflushed live state on close (WAL is disabled and
    // avoid_flush_during_shutdown is set), so reopening the live directory would lose it
    final File snapshotDir = new File(snapshotParentDirectory, "snapshot");
    layered.createSnapshot(snapshotDir);
    layered.close();
    layered = null;
    inner = null;
    final ZeebeDb<ColumnFamilies> reopened = dbFactory.createDb(snapshotDir);
    try {
      final TransactionContext context = reopened.createContext();
      final DbLong key = new DbLong();
      final DbLong value = new DbLong();
      final ColumnFamily<DbLong, DbLong> oneAfterRestart =
          reopened.createColumnFamily(ColumnFamilies.ONE, context, key, value);
      final DbString posKey = new DbString();
      final DbLong posValue = new DbLong();
      final ColumnFamily<DbString, DbLong> positionAfterRestart =
          reopened.createColumnFamily(ColumnFamilies.DEFAULT, context, posKey, posValue);

      // then the values and the position key are readable through plain contexts
      key.wrapLong(1);
      assertThat(oneAfterRestart.get(key).getValue()).isEqualTo(100);
      posKey.wrapString("lastProcessedPosition");
      assertThat(positionAfterRestart.get(posKey).getValue()).isEqualTo(42);
    } finally {
      reopened.close();
    }
  }

  @Test
  void shouldStillServeLayeredReadsAfterPersistRound() throws Exception {
    // given
    layeredContext.runInTransaction(() -> upsertOne(1, 100));

    // when
    final var coordinator = layered.defaultDomain().coordinator();
    final var round = coordinator.prepareRound(1);
    round.persist();
    coordinator.completeRound(round, true);

    // then the layered path reads the value back through its delegate
    assertThat(getOne(1)).isEqualTo(100);
  }

  // ------------------------------------------------------------------
  // Configuration and lifecycle guards
  // ------------------------------------------------------------------

  @Test
  void shouldReportOverCapacityWithTinyBudget() {
    // given a facade whose stores hold at most one byte; it shares the inner database with the
    // default facade, which owns closing it — so this one is deliberately never closed
    final var tiny =
        new LayeredZeebeDb<>(
            inner,
            new LayeredZeebeDbConfig(
                1, 0, false, 4, Duration.ofSeconds(1), Duration.ofMillis(250)));
    final TransactionContext tinyContext = tiny.layeredContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> tinyColumnFamily =
        tiny.createColumnFamily(ColumnFamilies.TWO, tinyContext, key, value);
    assertThat(tiny.defaultDomain().overCapacity()).isFalse();

    // when a committed write pins more bytes than the budget
    tinyContext.runInTransaction(
        () -> {
          key.wrapLong(1);
          value.wrapLong(-1);
          tinyColumnFamily.upsert(key, value);
        });

    // then
    assertThat(tiny.defaultDomain().overCapacity()).isTrue();
  }

  @Test
  void shouldRejectNewLayeredColumnFamilyAfterCoordinatorIsBuilt() {
    // given
    layered.defaultDomain().coordinator();

    // when / then
    assertThatThrownBy(
            () ->
                layered.createColumnFamily(
                    ColumnFamilies.TWO, layeredContext, new DbLong(), new DbLong()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("coordinator");
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private void upsertOne(final long key, final long value) {
    oneKey.wrapLong(key);
    oneValue.wrapLong(value);
    oneColumnFamily.upsert(oneKey, oneValue);
  }

  private long getOne(final long key) {
    oneKey.wrapLong(key);
    return oneColumnFamily.get(oneKey).getValue();
  }

  private void upsertPosition(final long position) {
    positionKey.wrapString("lastProcessedPosition");
    positionValue.wrapLong(position);
    positionColumnFamily.upsert(positionKey, positionValue);
  }

  private enum ColumnFamilies implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    ONE,
    TWO;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
