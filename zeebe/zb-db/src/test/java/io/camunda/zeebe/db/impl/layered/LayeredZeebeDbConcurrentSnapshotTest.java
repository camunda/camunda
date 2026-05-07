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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stress tests for the layered ZeebeDb snapshot mechanism under concurrent access.
 *
 * <p>These tests run an engine-like thread that continuously modifies state while a separate
 * snapshot thread takes snapshots. After each snapshot the test recovers from RocksDB and verifies
 * full state consistency. The goal is to expose race conditions between transaction commits and
 * snapshot flushes that would cause missing or phantom entries after recovery.
 */
final class LayeredZeebeDbConcurrentSnapshotTest {

  /** Duration to run each stress test — long enough to exercise many interleavings. */
  private static final Duration STRESS_DURATION = Duration.ofSeconds(5);

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  // ---------------------------------------------------------------------------
  //  Stress test 1 — atomic state version (upsert-only, never deleted)
  // ---------------------------------------------------------------------------

  /**
   * The engine thread updates ALL entries in a single transaction with an incrementing version
   * number. Entries are never deleted, only upserted. The snapshot thread takes snapshots
   * concurrently. After recovery from every snapshot, ALL entries must carry the same version
   * (proving the capture was atomic).
   */
  @Test
  void shouldCaptureAtomicStateVersion(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 50;
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: seed entries at version 0
      ctx.runInTransaction(
          () -> {
            for (int k = 0; k < entryCount; k++) {
              cf.upsert(dbLong(k), dbLong(0));
            }
          });

      final var stop = new AtomicBoolean(false);
      final var failed = new AtomicReference<Throwable>();
      final var version = new AtomicLong(0);

      // engine thread: bump all entries to the next version in one transaction
      final var engine =
          startThread(
              "engine",
              failed,
              () -> {
                while (!stop.get()) {
                  final long v = version.incrementAndGet();
                  ctx.runInTransaction(
                      () -> {
                        for (int k = 0; k < entryCount; k++) {
                          cf.upsert(dbLong(k), dbLong(v));
                        }
                      });
                }
              });

      // snapshot thread: take snapshots as fast as possible
      final List<File> snapshotDirs = new CopyOnWriteArrayList<>();
      final var snapCounter = new AtomicInteger(0);
      final var snapshotThread =
          startThread(
              "snapshot",
              failed,
              () -> {
                while (!stop.get()) {
                  final var dir = new File(snapshotParent, "snap-" + snapCounter.getAndIncrement());
                  db.createSnapshot(dir);
                  snapshotDirs.add(dir);
                }
              });

      Thread.sleep(STRESS_DURATION.toMillis());
      stop.set(true);
      engine.join(10_000);
      snapshotThread.join(10_000);
      assertThat(failed.get()).as("no exceptions during stress run").isNull();

      // then: verify every snapshot — all entries must exist and carry the same version
      assertThat(snapshotDirs).isNotEmpty();
      for (final var snapDir : snapshotDirs) {
        try (final var recovered = persistentFactory.createDb(snapDir)) {
          final var rCtx = recovered.createContext();
          final var rCf = longColumnFamily(recovered, rCtx);

          final var firstVal = rCf.get(dbLong(0));
          assertThat(firstVal)
              .as("entry 0 must exist in snapshot %s", snapDir.getName())
              .isNotNull();
          final long snapVersion = firstVal.getValue();

          for (int k = 1; k < entryCount; k++) {
            final var val = rCf.get(dbLong(k));
            assertThat(val)
                .as("entry %d must exist in snapshot %s", k, snapDir.getName())
                .isNotNull();
            assertThat(val.getValue())
                .as(
                    "entry %d version mismatch in %s (expected %d)",
                    k, snapDir.getName(), snapVersion)
                .isEqualTo(snapVersion);
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 2 — rapid delete/recreate, verify last snapshot
  // ---------------------------------------------------------------------------

  /**
   * The engine thread rapidly deletes and re-creates a set of entries (separate commits for each)
   * while the snapshot thread takes snapshots concurrently. Because delete and re-create are
   * separate commits, intermediate snapshots may legitimately capture a transient "deleted" state.
   * This test verifies the LAST snapshot (taken after the engine stops, when all entries have been
   * re-created) contains all entries.
   */
  @Test
  void shouldNotLoseEntriesDuringRapidDeleteRecreate(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 20;
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: seed all entries and flush to RocksDB
      ctx.runInTransaction(
          () -> {
            for (int k = 0; k < entryCount; k++) {
              cf.insert(dbLong(k), dbLong(k * 100L));
            }
          });
      db.createSnapshot(new File(snapshotParent, "seed"));

      final var stop = new AtomicBoolean(false);
      final var failed = new AtomicReference<Throwable>();

      // engine thread: delete then re-create each entry in separate commits
      final var engine =
          startThread(
              "engine",
              failed,
              () -> {
                long round = 0;
                while (!stop.get()) {
                  round++;
                  for (int k = 0; k < entryCount; k++) {
                    final int key = k;
                    ctx.runInTransaction(
                        () -> {
                          final var dk = dbLong(key);
                          cf.deleteExisting(dk);
                        });
                    final long val = round * 1000L + k;
                    ctx.runInTransaction(
                        () -> {
                          final var dk = dbLong(key);
                          cf.insert(dk, dbLong(val));
                        });
                  }
                }
              });

      // snapshot thread
      final var snapshotThread =
          startThread(
              "snapshot",
              failed,
              () -> {
                int i = 0;
                while (!stop.get()) {
                  final var dir = new File(snapshotParent, "snap-" + (i++));
                  db.createSnapshot(dir);
                }
              });

      Thread.sleep(STRESS_DURATION.toMillis());
      stop.set(true);
      engine.join(10_000);
      snapshotThread.join(10_000);
      assertThat(failed.get()).as("no exceptions during stress run").isNull();

      // take one final snapshot after the engine stopped — all entries should exist
      final var finalSnap = new File(snapshotParent, "final");
      db.createSnapshot(finalSnap);

      try (final var recovered = persistentFactory.createDb(finalSnap)) {
        final var rCf = longColumnFamily(recovered, recovered.createContext());
        for (int k = 0; k < entryCount; k++) {
          assertThat(rCf.get(dbLong(k)))
              .as("entry %d must survive rapid delete/recreate + flush", k)
              .isNotNull();
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 3 — upsert-only entries must never vanish from any snapshot
  // ---------------------------------------------------------------------------

  /**
   * Mixes upsert-only entries (first half) and delete-recreate entries (second half). Verifies
   * EVERY intermediate snapshot: the upsert-only entries must ALWAYS be present. If any upsert-only
   * entry goes missing, that proves data was lost during the flush.
   */
  @Test
  void shouldNeverLoseUpsertOnlyEntriesInAnySnapshot(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 30;
    final int upsertCount = entryCount / 2; // entries 0..upsertCount-1 are never deleted
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: seed entries
      ctx.runInTransaction(
          () -> {
            for (int k = 0; k < entryCount; k++) {
              cf.insert(dbLong(k), dbLong(0));
            }
          });

      final var stop = new AtomicBoolean(false);
      final var failed = new AtomicReference<Throwable>();
      final List<File> snapshotDirs = new CopyOnWriteArrayList<>();

      // engine: upsert the first half; delete-recreate the second half
      final var engine =
          startThread(
              "engine",
              failed,
              () -> {
                long v = 0;
                while (!stop.get()) {
                  v++;
                  final long version = v;
                  // upsert-only half — all in one transaction
                  ctx.runInTransaction(
                      () -> {
                        for (int k = 0; k < upsertCount; k++) {
                          cf.upsert(dbLong(k), dbLong(version));
                        }
                      });
                  // delete-recreate half — separate transactions per entry
                  for (int k = upsertCount; k < entryCount; k++) {
                    final int key = k;
                    final long val = version;
                    ctx.runInTransaction(() -> cf.deleteExisting(dbLong(key)));
                    ctx.runInTransaction(() -> cf.insert(dbLong(key), dbLong(val)));
                  }
                }
              });

      // snapshot thread
      final var snapCounter = new AtomicInteger(0);
      final var snapshotThread =
          startThread(
              "snapshot",
              failed,
              () -> {
                while (!stop.get()) {
                  final var dir = new File(snapshotParent, "snap-" + snapCounter.getAndIncrement());
                  db.createSnapshot(dir);
                  snapshotDirs.add(dir);
                }
              });

      Thread.sleep(STRESS_DURATION.toMillis());
      stop.set(true);
      engine.join(10_000);
      snapshotThread.join(10_000);
      assertThat(failed.get()).as("no exceptions during stress run").isNull();

      // then: the upsert-only entries must be present in EVERY snapshot
      assertThat(snapshotDirs).as("at least some snapshots were taken").isNotEmpty();
      for (final var snapDir : snapshotDirs) {
        try (final var recovered = persistentFactory.createDb(snapDir)) {
          final var rCf = longColumnFamily(recovered, recovered.createContext());
          for (int k = 0; k < upsertCount; k++) {
            assertThat(rCf.get(dbLong(k)))
                .as("upsert-only entry %d missing in snapshot %s", k, snapDir.getName())
                .isNotNull();
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 4 — recovery + continued processing (multiple cycles)
  // ---------------------------------------------------------------------------

  /**
   * Simulates the real lifecycle: process → snapshot → recover → process more → snapshot → recover.
   * Verifies state after each recovery cycle. This catches bugs in how the layered DB re-opens from
   * a snapshot and continues to accumulate state.
   */
  @Test
  void shouldSurviveMultipleRecoveryCycles(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 20;
    final int cycles = 10;
    final var factory = layeredFactory();
    var currentDbDir = dbDir;

    for (int cycle = 0; cycle < cycles; cycle++) {
      try (final var db = openLayeredDb(factory, currentDbDir)) {
        final var ctx = db.createContext();
        final var cf = longColumnFamily(db, ctx);

        // process: upsert all entries with cycle-based value
        final long value = (cycle + 1) * 1000L;
        ctx.runInTransaction(
            () -> {
              for (int k = 0; k < entryCount; k++) {
                cf.upsert(dbLong(k), dbLong(value + k));
              }
            });

        // also delete and re-create a subset
        for (int k = 0; k < entryCount / 4; k++) {
          final int key = k;
          final long newVal = value + key + 500;
          ctx.runInTransaction(() -> cf.deleteExisting(dbLong(key)));
          ctx.runInTransaction(() -> cf.insert(dbLong(key), dbLong(newVal)));
        }

        // snapshot
        final var snapDir = new File(snapshotParent, "cycle-" + cycle);
        db.createSnapshot(snapDir);
        currentDbDir = snapDir;
      }

      // verify: recover with a LAYERED DB (not just RocksDB) and check all entries
      try (final var recovered = openLayeredDb(factory, currentDbDir)) {
        final var rCtx = recovered.createContext();
        final var rCf = longColumnFamily(recovered, rCtx);
        for (int k = 0; k < entryCount; k++) {
          assertThat(rCf.get(dbLong(k)))
              .as("entry %d missing after recovery cycle %d", k, cycle)
              .isNotNull();
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 5 — recovery + replay simulation
  // ---------------------------------------------------------------------------

  /**
   * Simulates the real recovery flow: snapshot → open layered DB → "replay" events by re-applying
   * inserts and updates. This is the closest we can get to the actual engine replay path without
   * the full engine, and tests the specific scenario from the NPE stacktrace.
   */
  @Test
  void shouldSupportReplayAfterRecoveryFromConcurrentSnapshot(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 30;
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);

      // given: create entries and flush
      ctx.runInTransaction(
          () -> {
            for (int k = 0; k < entryCount; k++) {
              cf.insert(dbLong(k), dbLong(k));
            }
          });

      final var stop = new AtomicBoolean(false);
      final var failed = new AtomicReference<Throwable>();
      final var lastSnapshot = new AtomicReference<File>();

      // engine thread: mixed operations
      final var engine =
          startThread(
              "engine",
              failed,
              () -> {
                long v = 0;
                while (!stop.get()) {
                  v++;
                  final long version = v;
                  ctx.runInTransaction(
                      () -> {
                        for (int k = 0; k < entryCount; k++) {
                          cf.upsert(dbLong(k), dbLong(version * 100 + k));
                        }
                      });
                }
              });

      // snapshot thread
      final var snapCounter = new AtomicInteger(0);
      final var snapshotThread =
          startThread(
              "snapshot",
              failed,
              () -> {
                while (!stop.get()) {
                  final var dir = new File(snapshotParent, "snap-" + snapCounter.getAndIncrement());
                  db.createSnapshot(dir);
                  lastSnapshot.set(dir);
                }
              });

      Thread.sleep(STRESS_DURATION.toMillis());
      stop.set(true);
      engine.join(10_000);
      snapshotThread.join(10_000);
      assertThat(failed.get()).as("no exceptions during stress run").isNull();

      // then: open a NEW layered DB from the snapshot and simulate replay
      final var snapDir = lastSnapshot.get();
      assertThat(snapDir).isNotNull();
      try (final var recovered = openLayeredDb(factory, snapDir)) {
        final var rCtx = recovered.createContext();
        final var rCf = longColumnFamily(recovered, rCtx);

        // verify all entries are readable through the layered DB
        for (int k = 0; k < entryCount; k++) {
          assertThat(rCf.get(dbLong(k)))
              .as("entry %d must be readable via layered DB after recovery", k)
              .isNotNull();
        }

        // simulate replay: insert new entries and update existing ones
        rCtx.runInTransaction(
            () -> {
              for (int k = 0; k < entryCount; k++) {
                rCf.upsert(dbLong(k), dbLong(99999L + k));
              }
              // also insert brand-new entries
              for (int k = entryCount; k < entryCount + 10; k++) {
                rCf.insert(dbLong(k), dbLong(k));
              }
            });

        // verify everything is accessible
        for (int k = 0; k < entryCount + 10; k++) {
          assertThat(rCf.get(dbLong(k)))
              .as("entry %d must be readable after replay", k)
              .isNotNull();
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 6 — insert-over-persistent after recovery
  // ---------------------------------------------------------------------------

  /**
   * After snapshot recovery the in-memory layer is empty. The engine must be able to insert keys
   * that exist only in the persistent layer without false-positive precondition errors.
   */
  @Test
  void shouldInsertOverPersistentEntryAfterRecovery(
      @TempDir final File dbDir, @TempDir final File snapshotDir) throws Exception {
    final var factory = layeredFactory();

    try (final var db = openLayeredDb(factory, dbDir)) {
      final var ctx = db.createContext();
      final var cf = longColumnFamily(db, ctx);
      ctx.runInTransaction(() -> cf.insert(dbLong(1L), dbLong(42L)));

      final var snap = new File(snapshotDir, "snap");
      db.createSnapshot(snap);

      try (final var recovered = openLayeredDb(factory, snap)) {
        final var rCtx = recovered.createContext();
        final var rCf = longColumnFamily(recovered, rCtx);

        rCtx.runInTransaction(() -> rCf.insert(dbLong(1L), dbLong(99L)));
        assertThat(rCf.get(dbLong(1L)).getValue()).isEqualTo(99L);
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Stress test 7 — close during concurrent snapshot (the raft-transition race)
  // ---------------------------------------------------------------------------

  /**
   * Reproduces the race between {@code LayeredZeebeDb.close()} (called during a raft partition
   * transition) and a concurrent snapshot flush. Without proper locking, {@code
   * InMemoryZeebeDb.committedData.clear()} runs while the flush is iterating, producing a partial
   * capture: processedPosition is captured but some state entries are lost.
   *
   * <p>The test validates that close() blocks until the in-progress flush completes, and that the
   * resulting snapshot (if any) is consistent.
   */
  @RepeatedTest(5)
  void shouldNotProducePartialSnapshotWhenClosedDuringFlush(
      @TempDir final File dbDir, @TempDir final File snapshotParent) throws Exception {
    final int entryCount = 100;
    final var factory = layeredFactory();
    final var db = openLayeredDb(factory, dbDir);
    final var ctx = db.createContext();
    final var cf = longColumnFamily(db, ctx);

    // given: many committed entries
    ctx.runInTransaction(
        () -> {
          for (int k = 0; k < entryCount; k++) {
            cf.upsert(dbLong(k), dbLong(k * 10L));
          }
        });

    // when: snapshot and close race against each other
    final var snapshotDir = new File(snapshotParent, "snap");
    final var snapshotDone = new java.util.concurrent.atomic.AtomicBoolean(false);
    final var snapshotError = new AtomicReference<Throwable>();

    final var snapshotThread =
        new Thread(
            () -> {
              try {
                db.createSnapshot(snapshotDir);
                snapshotDone.set(true);
              } catch (final Exception e) {
                // close() racing with createSnapshot() may cause an exception —
                // that is acceptable as long as no partial snapshot is produced
                snapshotError.set(e);
              }
            },
            "snapshot");
    snapshotThread.start();

    // give the snapshot thread a tiny head-start then close on the main thread
    Thread.yield();

    try {
      db.close();
    } catch (final Exception ignored) {
      // may already be closed
    }
    // Wait for the snapshot to finish first — we're testing that close() blocks
    // on the write lock until the snapshot (which holds the read lock) completes.
    // The key invariant is that the snapshot produced is atomic, not partial.
    snapshotThread.join(10_000);

    // then: if a snapshot was produced, it must be ALL-or-NOTHING
    if (snapshotDone.get()
        && snapshotDir.exists()
        && snapshotDir.listFiles() != null
        && snapshotDir.listFiles().length > 0) {
      try (final var recovered = persistentFactory.createDb(snapshotDir)) {
        final var rCf = longColumnFamily(recovered, recovered.createContext());

        int presentCount = 0;
        for (int k = 0; k < entryCount; k++) {
          if (rCf.get(dbLong(k)) != null) {
            presentCount++;
          }
        }
        // Atomic: either ALL entries are present or NONE.
        // A partial count means close() corrupted the capture.
        assertThat(presentCount)
            .as("snapshot must be atomic: all %d entries or none, got %d", entryCount, presentCount)
            .satisfiesAnyOf(c -> assertThat(c).isEqualTo(entryCount), c -> assertThat(c).isZero());
      }
    }
  }

  // ---------------------------------------------------------------------------
  //  Helpers
  // ---------------------------------------------------------------------------

  private Thread startThread(
      final String name, final AtomicReference<Throwable> failed, final Runnable body) {
    final var t =
        new Thread(
            () -> {
              try {
                body.run();
              } catch (final Throwable e) {
                failed.compareAndSet(null, e);
              }
            },
            name);
    t.setDaemon(true);
    t.start();
    return t;
  }

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
