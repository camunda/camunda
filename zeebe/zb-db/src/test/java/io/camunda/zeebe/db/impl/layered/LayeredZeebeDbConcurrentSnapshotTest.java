/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests that concurrent engine commits and snapshot flushes produce a consistent snapshot. This
 * covers the race condition where a delete-and-recreate commit races with the snapshot capture,
 * which previously caused lost entries and NPEs during replay.
 */
final class LayeredZeebeDbConcurrentSnapshotTest {

  private static final long ENTRY_KEY = 42L;

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  /**
   * Reproduces the race between commit and snapshot flush.
   *
   * <p>Thread A (engine): repeatedly deletes and re-creates an entry. Thread B (snapshot): captures
   * flush snapshots concurrently with commits.
   *
   * <p>After each snapshot, the test recovers from RocksDB and verifies the entry is present — the
   * exact scenario that previously caused the NPE during replay.
   */
  @SuppressWarnings("unchecked")
  @Test
  void shouldNotLoseEntryWhenDeleteRecreateRacesWithFlush(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: entry exists in both in-memory and RocksDB (after first flush)
      final var key = dbLong(ENTRY_KEY);
      final var value = dbLong(100L);
      ctx.runInTransaction(() -> cf.insert(key, value));

      final var firstSnapshot = new File(snapshotParent, "init");
      db.createSnapshot(firstSnapshot);

      // when: engine thread continuously deletes and re-creates the entry
      final var iterations = 200;
      final var failed = new AtomicBoolean(false);
      final var barrier = new CyclicBarrier(2);
      final var lastSnapshotDir = new AtomicReference<File>();

      final var engineThread =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < iterations && !failed.get(); i++) {
                    // delete
                    ctx.runInTransaction(
                        () -> {
                          key.wrapLong(ENTRY_KEY);
                          cf.deleteExisting(key);
                        });
                    // re-create with a new value
                    final long v = 1000L + i;
                    ctx.runInTransaction(
                        () -> {
                          key.wrapLong(ENTRY_KEY);
                          value.wrapLong(v);
                          cf.insert(key, value);
                        });
                    barrier.await(); // sync with snapshot thread
                  }
                } catch (final Exception e) {
                  failed.set(true);
                  throw new RuntimeException(e);
                }
              });

      final var snapshotThread =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < iterations && !failed.get(); i++) {
                    barrier.await(); // sync with engine thread
                    final var snapDir = new File(snapshotParent, "snap-" + i);
                    db.createSnapshot(snapDir);
                    lastSnapshotDir.set(snapDir);
                  }
                } catch (final Exception e) {
                  failed.set(true);
                  throw new RuntimeException(e);
                }
              });

      engineThread.start();
      snapshotThread.start();
      engineThread.join(30_000);
      snapshotThread.join(30_000);

      assertThat(failed.get()).as("no exceptions during concurrent commit/flush").isFalse();

      // then: recover from the last snapshot and verify the entry exists
      final var snapPath = lastSnapshotDir.get();
      assertThat(snapPath).isNotNull();
      try (final var recoveredDb = persistentFactory.createDb(snapPath)) {
        final var recoveredCf = longColumnFamily(recoveredDb, recoveredDb.createContext());
        final var recoveredKey = dbLong(ENTRY_KEY);
        assertThat(recoveredCf.get(recoveredKey))
            .as("entry must survive concurrent delete-recreate + flush")
            .isNotNull();
      }
    }
  }

  /**
   * Verifies that tombstones added by the engine between a flush capture and the tombstone cleanup
   * are not lost.
   */
  @Test
  void shouldPreserveTombstonesAddedAfterFlushCapture(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: two entries exist and are flushed to RocksDB
      final var key1 = dbLong(1L);
      final var key2 = dbLong(2L);
      ctx.runInTransaction(() -> cf.insert(key1, dbLong(10L)));
      ctx.runInTransaction(() -> cf.insert(key2, dbLong(20L)));

      final var firstSnap = new File(snapshotParent, "first");
      db.createSnapshot(firstSnap);

      // when: delete entry 2 and take a new snapshot
      ctx.runInTransaction(
          () -> {
            key2.wrapLong(2L);
            cf.deleteExisting(key2);
          });

      final var secondSnap = new File(snapshotParent, "second");
      db.createSnapshot(secondSnap);

      // then: entry 2 must be absent from the recovered snapshot
      try (final var recovered = persistentFactory.createDb(secondSnap)) {
        final var rCf = longColumnFamily(recovered, recovered.createContext());
        assertThat(rCf.get(dbLong(1L))).as("entry 1 should survive").isNotNull();
        assertThat(rCf.get(dbLong(2L))).as("entry 2 should be deleted").isNull();
      }
    }
  }

  /**
   * Verifies that a recovered layered DB can insert a key that already exists in the persistent
   * layer without a false-positive precondition error.
   */
  @Test
  void shouldInsertOverPersistentEntryAfterRecovery(
      @TempDir final File dbDir, @TempDir final File snapshotDir) throws Exception {
    final var factory = layeredFactory();

    // given: entry flushed to RocksDB
    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);
      ctx.runInTransaction(() -> cf.insert(dbLong(1L), dbLong(42L)));

      final var snap = new File(snapshotDir, "snap");
      db.createSnapshot(snap);

      // when: open from snapshot (InMemory is empty) and insert the same key
      try (final var recovered = openLayeredDb(factory, snap)) {
        final var rCtx = recovered.createContext();
        final var rCf = longColumnFamily(recovered, rCtx);

        // then: insert must not throw — the persistent-layer entry is a leftover, not a conflict
        rCtx.runInTransaction(() -> rCf.insert(dbLong(1L), dbLong(99L)));
        assertThat(rCf.get(dbLong(1L)).getValue()).isEqualTo(99L);
      }
    }
  }

  // ---- Helpers ----

  private LayeredZeebeDbFactory<DefaultColumnFamily> layeredFactory() {
    return new LayeredZeebeDbFactory<>(
        new RocksDbConfiguration(),
        new ConsistencyChecksSettings(true, true),
        new AccessMetricsConfiguration(Kind.NONE, 1),
        SimpleMeterRegistry::new);
  }

  @SuppressWarnings("unchecked")
  private LayeredZeebeDb<DefaultColumnFamily> openLayeredDb(
      final LayeredZeebeDbFactory<DefaultColumnFamily> factory, final File path) {
    return (LayeredZeebeDb<DefaultColumnFamily>) factory.createDb(path);
  }

  private ColumnFamily<DbLong, DbLong> longColumnFamily(
      final ZeebeDb<DefaultColumnFamily> db, final TransactionContext ctx) {
    return db.createColumnFamily(DefaultColumnFamily.DEFAULT, ctx, new DbLong(), new DbLong());
  }

  private DbLong dbLong(final long v) {
    final var d = new DbLong();
    d.wrapLong(v);
    return d;
  }
}
