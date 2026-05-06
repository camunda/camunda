/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.layered.LayeredZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the LayeredZeebeDb correctly persists data through the snapshot+restart cycle used
 * by the broker. The broker takes a snapshot (which flushes in-memory data to RocksDB), then on
 * restart opens the DB from the snapshot directory.
 */
final class LayeredZeebeDbSnapshotTest {

  enum Families implements EnumValue, ScopedColumnFamily {
    ONE(1),
    TWO(2);

    private final int value;

    Families(final int value) {
      this.value = value;
    }

    @Override
    public int getValue() {
      return value;
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }

  private final ZeebeDbFactory<Families> factory = DefaultZeebeDbFactory.layeredFactory();

  @Test
  void shouldReadDataFromSnapshotAfterRestart(
      @TempDir final File dbDir, @TempDir final File snapshotDir) throws Exception {
    // given: open a fresh DB, write some data
    final ZeebeDb<Families> db = factory.createDb(dbDir);
    final TransactionContext ctx = db.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> cf = db.createColumnFamily(Families.ONE, ctx, key, value);

    key.wrapLong(1);
    value.wrapLong(42);
    cf.insert(key, value);

    // when: take a snapshot (this flushes in-memory data to RocksDB) and close
    final var snapshotPath = new File(snapshotDir, "snapshot");
    db.createSnapshot(snapshotPath);
    db.close();

    // then: reopening from the snapshot directory should see the written data
    final ZeebeDb<Families> restoredDb = factory.createDb(snapshotPath);
    try {
      final TransactionContext restoredCtx = restoredDb.createContext();
      final DbLong restoredKey = new DbLong();
      final DbLong restoredValue = new DbLong();
      final ColumnFamily<DbLong, DbLong> restoredCf =
          restoredDb.createColumnFamily(Families.ONE, restoredCtx, restoredKey, restoredValue);

      restoredKey.wrapLong(1);
      final DbLong result = restoredCf.get(restoredKey);

      assertThat(result)
          .as("Data written before snapshot should be readable after restart from snapshot")
          .isNotNull();
      assertThat(result.getValue()).isEqualTo(42);
    } finally {
      restoredDb.close();
    }
  }

  /**
   * Reproduces: with preconditions disabled (the production default), insert() on a key that
   * already exists in the persistent snapshot layer must NOT throw — it should behave like upsert.
   * Before the fix, LayeredColumnFamily.insert() always called existsInternal() regardless of
   * enablePreconditions, causing ZeebeDbInconsistentException during replay after a snapshot
   * restart.
   */
  @Test
  void shouldNotThrowOnDuplicateInsertWhenPreconditionsDisabled(
      @TempDir final File dbDir, @TempDir final File snapshotDir) throws Exception {
    // given: a factory with preconditions disabled (mirrors production broker config)
    final ZeebeDbFactory<Families> noPreconditionsFactory =
        new LayeredZeebeDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(false, false),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new);

    final ZeebeDb<Families> db = noPreconditionsFactory.createDb(dbDir);
    final TransactionContext ctx = db.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> cf = db.createColumnFamily(Families.ONE, ctx, key, value);

    key.wrapLong(1);
    value.wrapLong(42);
    cf.insert(key, value);

    // when: snapshot (flushes to RocksDB) + reopen from snapshot
    final var snapshotPath = new File(snapshotDir, "snapshot");
    db.createSnapshot(snapshotPath);
    db.close();

    final ZeebeDb<Families> restoredDb = noPreconditionsFactory.createDb(snapshotPath);
    try {
      final TransactionContext restoredCtx = restoredDb.createContext();
      final DbLong rk = new DbLong();
      final DbLong rv = new DbLong();
      final ColumnFamily<DbLong, DbLong> rCf =
          restoredDb.createColumnFamily(Families.ONE, restoredCtx, rk, rv);

      // then: inserting the same key that is already in the snapshot MUST NOT throw when
      // preconditions are disabled — this simulates replay after a snapshot restart
      rk.wrapLong(1);
      rv.wrapLong(99);
      assertThatNoException().isThrownBy(() -> rCf.insert(rk, rv));
    } finally {
      restoredDb.close();
    }
  }

  @Test
  void shouldReadMultipleEntriesFromSnapshotAfterRestart(
      @TempDir final File dbDir, @TempDir final File snapshotDir) throws Exception {
    // given: write multiple entries across two column families
    final ZeebeDb<Families> db = factory.createDb(dbDir);
    final TransactionContext ctx = db.createContext();
    final DbLong key = new DbLong();
    final DbLong value = new DbLong();
    final ColumnFamily<DbLong, DbLong> cf1 = db.createColumnFamily(Families.ONE, ctx, key, value);
    final ColumnFamily<DbLong, DbLong> cf2 = db.createColumnFamily(Families.TWO, ctx, key, value);

    for (int i = 1; i <= 5; i++) {
      key.wrapLong(i);
      value.wrapLong(i * 10L);
      cf1.insert(key, value);
    }
    key.wrapLong(99);
    value.wrapLong(999);
    cf2.insert(key, value);

    // when: snapshot and restart
    final var snapshotPath = new File(snapshotDir, "snapshot");
    db.createSnapshot(snapshotPath);
    db.close();

    // then
    final ZeebeDb<Families> restoredDb = factory.createDb(snapshotPath);
    try {
      final TransactionContext restoredCtx = restoredDb.createContext();
      final DbLong rk = new DbLong();
      final DbLong rv = new DbLong();
      final ColumnFamily<DbLong, DbLong> rCf1 =
          restoredDb.createColumnFamily(Families.ONE, restoredCtx, rk, rv);
      final ColumnFamily<DbLong, DbLong> rCf2 =
          restoredDb.createColumnFamily(Families.TWO, restoredCtx, rk, rv);

      for (int i = 1; i <= 5; i++) {
        rk.wrapLong(i);
        final DbLong result = rCf1.get(rk);
        assertThat(result).as("Entry %d in CF1 should survive snapshot+restart", i).isNotNull();
        assertThat(result.getValue()).isEqualTo(i * 10L);
      }

      rk.wrapLong(99);
      final DbLong cf2Result = rCf2.get(rk);
      assertThat(cf2Result).as("Entry in CF2 should survive snapshot+restart").isNotNull();
      assertThat(cf2Result.getValue()).isEqualTo(999);
    } finally {
      restoredDb.close();
    }
  }
}
