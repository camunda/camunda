/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.PersistTrigger;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.util.DefaultZeebeDbFactory;
import java.io.File;
import java.time.Duration;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * The IO actor's paced slice loop: multiple committed sub-batch slices per round, pacing waits
 * driven by the actor's (controlled) timer wheel, and a close mid-round failing the in-flight round
 * instead of leaving it dangling. Time is the extension's controlled clock — no sleeps.
 */
final class LayeredPersistIoActorTest {

  @RegisterExtension
  private final ControlledActorSchedulerExtension scheduler =
      new ControlledActorSchedulerExtension();

  @TempDir private File dbDirectory;

  private ZeebeDb<ZbColumnFamilies> inner;
  private LayeredZeebeDb<ZbColumnFamilies> layered;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private TransactionContext context;
  private LayeredPersistIoActor io;

  @BeforeEach
  void setUp() {
    inner = DefaultZeebeDbFactory.defaultFactory().createDb(dbDirectory);
    layered =
        new LayeredZeebeDb<>(
            inner, new LayeredZeebeDbConfig(1024 * 1024, 0, true, 4, Duration.ofSeconds(1)));
    context = layered.layeredContext();
    columnFamily =
        layered.createColumnFamily(ZbColumnFamilies.DEFAULT, context, new DbLong(), new DbLong());
    // one entry per slice: a DbLong key/value pair is 16 bytes, well over the 1-byte minimum
    io = new LayeredPersistIoActor(new PartitionId("test", 1), 1);
    scheduler.submitActor(io);
    scheduler.workUntilDone();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(layered);
  }

  @Test
  void shouldDrainRoundInMultipleSlicesWithoutPacingWaits() {
    // given three buffered entries and an expedited pacer (zero budget — no waits, continuations
    // go to the end of the actor queue)
    for (long key = 1; key <= 3; key++) {
      commitBatch(key, key * 100);
    }
    final PersistRound round =
        layered.defaultDomain().preparePersist(3, PersistTrigger.PRE_SNAPSHOT);
    final DrainPacer pacer = new DrainPacer(0, System.nanoTime());

    // when
    final ActorFuture<Void> done = io.persist(round, pacer);
    scheduler.workUntilDone();

    // then the round completed through multiple committed slices (3 data + 1 anchor-only)
    assertThat(done.isDone()).isTrue();
    assertThat(done.isCompletedExceptionally()).isFalse();
    layered.defaultDomain().completePersist(round, true);
    assertThat(sliceCount()).isEqualTo(4.0);
    for (long key = 1; key <= 3; key++) {
      assertThat(passThroughGet(key)).isEqualTo(key * 100);
    }
  }

  @Test
  void shouldWaitBetweenSlicesUntilThePacerAllows() {
    // given two buffered entries and a real pacing budget
    commitBatch(1, 100);
    commitBatch(2, 200);
    final PersistRound round =
        layered.defaultDomain().preparePersist(2, PersistTrigger.PRE_SNAPSHOT);
    final DrainPacer pacer = new DrainPacer(Duration.ofHours(1).toNanos(), System.nanoTime());

    // when the drain runs everything currently runnable
    final ActorFuture<Void> done = io.persist(round, pacer);
    scheduler.workUntilDone();

    // then it committed a first slice and parked on the pacer's timer — partial state durable,
    // the round (and its anchor) still pending
    assertThat(done.isDone()).isFalse();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isNull();

    // when the pacing wait elapses on the controlled clock
    scheduler.updateClock(Duration.ofHours(1));
    scheduler.workUntilDone();
    scheduler.updateClock(Duration.ofHours(1));
    scheduler.workUntilDone();

    // then the drain resumed and finished
    assertThat(done.isDone()).isTrue();
    assertThat(done.isCompletedExceptionally()).isFalse();
    layered.defaultDomain().completePersist(round, true);
    assertThat(passThroughGet(2)).isEqualTo(200);
  }

  @Test
  void shouldFailInFlightRoundWhenClosingMidRound() {
    // given a paced round parked between slices on a long pacing wait
    commitBatch(1, 100);
    commitBatch(2, 200);
    final PersistRound round =
        layered.defaultDomain().preparePersist(2, PersistTrigger.PRE_SNAPSHOT);
    final DrainPacer pacer = new DrainPacer(Duration.ofHours(1).toNanos(), System.nanoTime());
    final ActorFuture<Void> done = io.persist(round, pacer);
    scheduler.workUntilDone();
    assertThat(done.isDone()).isFalse();

    // when the IO actor closes mid-round
    final ActorFuture<Void> closed = io.closeAsync();
    scheduler.workUntilDone();

    // then the round fails instead of dangling: the driver completes it as failed, everything
    // stays buffered, and a successor would complete the stale round forward
    assertThat(closed.isDone()).isTrue();
    assertThat(done.isCompletedExceptionally()).isTrue();
    layered.defaultDomain().completePersist(round, false);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();
  }

  private void commitBatch(final long key, final long value) {
    context.runInTransaction(
        () -> {
          final DbLong dbKey = new DbLong();
          final DbLong dbValue = new DbLong();
          dbKey.wrapLong(key);
          dbValue.wrapLong(value);
          columnFamily.upsert(dbKey, dbValue);
        });
  }

  /** Reads committed RocksDB only — what a slice has durably committed so far. */
  private Long passThroughGet(final long key) {
    final TransactionContext passThrough = layered.createContext();
    final ColumnFamily<DbLong, DbLong> passThroughColumnFamily =
        layered.createColumnFamily(
            ZbColumnFamilies.DEFAULT, passThrough, new DbLong(), new DbLong());
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = passThroughColumnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  private double sliceCount() {
    return inner
        .getMeterRegistry()
        .get("zeebe.db.layered.persist.slices")
        .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
        .counter()
        .count();
  }
}
