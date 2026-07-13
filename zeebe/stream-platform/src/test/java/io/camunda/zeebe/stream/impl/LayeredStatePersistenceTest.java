/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.util.DefaultZeebeDbFactory;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The asynchronous persist driver: rounds prepared on the owner thread, drained on an IO thread
 * while the owner keeps committing batches, and completed back on the owner thread; the
 * scheduled-task barrier and the pre-snapshot flush awaiting in-flight rounds by chaining futures;
 * failed rounds staying buffered and being retried.
 *
 * <p>The test plays both actors deterministically: the JUnit thread is the owner thread and drains
 * a queue standing in for the processor actor's job queue; a latch-gated single-thread executor
 * stands in for the IO actor. No sleeps — all waiting is bounded latch/poll waiting.
 */
final class LayeredStatePersistenceTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  @TempDir private File dbDirectory;

  private ZeebeDb<ZbColumnFamilies> inner;
  private LayeredZeebeDb<ZbColumnFamilies> layered;
  private ColumnFamily<DbLong, DbLong> columnFamily;
  private TransactionContext context;

  private final AtomicLong lastProcessedPosition = new AtomicLong();
  private final LinkedBlockingQueue<Runnable> processorJobs = new LinkedBlockingQueue<>();
  private ControlledIo io;
  private LayeredStatePersistence persistence;

  @BeforeEach
  void setUp() {
    inner = DefaultZeebeDbFactory.defaultFactory().createDb(dbDirectory);
    layered =
        new LayeredZeebeDb<>(
            inner,
            new LayeredZeebeDbConfig(256, true, 4, Duration.ofSeconds(1), Duration.ofMillis(250)));
    context = layered.layeredContext();
    columnFamily =
        layered.createColumnFamily(ZbColumnFamilies.DEFAULT, context, new DbLong(), new DbLong());
    io = new ControlledIo();
    persistence =
        new LayeredStatePersistence(layered, lastProcessedPosition::get, processorJobs::add, io);
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(io, layered);
  }

  // ------------------------------------------------------------------
  // Processing continues while a round persists on the IO thread
  // ------------------------------------------------------------------

  @Test
  void shouldKeepCommittingBatchesWhileRoundPersistsOnIoThread() throws Exception {
    // given a buffered batch and an IO thread blocked inside the persist step
    commitBatch(1, 100);
    io.blockNextRound();
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    assertThat(persistence.roundInFlight()).isTrue();
    assertThat(inFlightGauge()).isEqualTo(1.0);

    // when the owner thread keeps committing and freezing while the round is in flight
    commitBatch(2, 200);
    persistence.onFreezeTick();
    commitBatch(3, 300);

    // then the new writes are visible to the owner immediately and the round completes on the
    // processor queue once the IO thread finishes
    assertThat(ownerGet(2)).isEqualTo(200);
    io.releaseRound();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // the drained cut reached the durable store, the late batches stayed buffered
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isNull();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();
    assertThat(inFlightGauge()).isEqualTo(0.0);
  }

  @Test
  void shouldStartNextRoundOnTickAfterRoundCompleted() throws Exception {
    // given a completed first round and a batch committed while it was in flight
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    commitBatch(2, 200);
    io.releaseRound();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // when the next tick fires
    persistence.onPeriodicTick();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the late batch drained too
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldSkipTickWhileRoundInFlight() throws Exception {
    // given a round blocked on the IO thread
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    commitBatch(2, 200);

    // when further ticks fire while the round is in flight
    persistence.onPeriodicTick();
    persistence.onPeriodicTick();

    // then no second round was started (a stacked round would throw in the coordinator) and the
    // single in-flight round completes normally
    io.releaseRound();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(io.roundsPersisted()).isEqualTo(1);
  }

  // ------------------------------------------------------------------
  // Scheduled-task barrier
  // ------------------------------------------------------------------

  @Test
  void shouldLetScheduledTaskRunImmediatelyWhenNothingIsBuffered() {
    // given no buffered writes and no round in flight
    // when / then the barrier reports ready without a future
    assertThat(persistence.prepareForScheduledTask()).isNull();
  }

  @Test
  void shouldAwaitInFlightRoundAndDrainRemainderBeforeScheduledTask() throws Exception {
    // given a round in flight on the IO thread and a batch committed during it
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    commitBatch(2, 200);

    // when a scheduled task prepares
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();
    assertThat(ready.isDone()).isFalse();

    // then it completes only after the in-flight round landed AND the remainder was drained in a
    // follow-up round, so the task's persisted-state scan observes both batches
    io.releaseRound();
    runProcessorJobsUntil(ready::isDone);
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(roundCount("scheduledTask")).isEqualTo(1.0);
  }

  @Test
  void shouldDeferScheduledTaskWhileBatchInFlight() throws Exception {
    // given an open batch on the owner thread
    context.getCurrentTransaction().run(() -> put(1, 100));
    assertThat(layered.defaultDomain().batchInFlight()).isTrue();

    // when a scheduled task prepares mid-batch
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();

    // then the preparation yields while the batch is open and proceeds once it committed
    runOneProcessorJob();
    assertThat(ready.isDone()).isFalse();
    context.getCurrentTransaction().commit();
    runProcessorJobsUntil(ready::isDone);
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  @Test
  void shouldRunScheduledTaskOnStaleStateWhenItsRoundFails() throws Exception {
    // given buffered writes and failing persistence
    commitBatch(1, 100);
    io.failNextRound();

    // when a scheduled task prepares
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();

    // then the preparation makes one attempt and lets the task run on a stale scan — a failed
    // round must not block all scheduled work — while the segments stay buffered for a retry
    runProcessorJobsUntil(ready::isDone);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();
    assertThat(passThroughGet(1)).isNull();
  }

  // ------------------------------------------------------------------
  // Pre-snapshot flush
  // ------------------------------------------------------------------

  @Test
  void shouldAwaitInFlightRoundAndDrainRemainderForSnapshotFlush() throws Exception {
    // given a round in flight and a batch committed during it
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    commitBatch(2, 200);

    // when the pre-snapshot flush is requested
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    assertThat(flushed.isDone()).isFalse();

    // then it completes only once the durable store holds everything committed before the flush
    io.releaseRound();
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldFailSnapshotFlushWhenItsRoundFails() throws Exception {
    // given buffered writes and failing persistence
    commitBatch(1, 100);
    io.failNextRound();

    // when the pre-snapshot flush is requested
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    runProcessorJobsUntil(flushed::isDone);

    // then the flush fails — the snapshot must not proceed on a stale durable cut — and the
    // segments stay buffered until a later round succeeds
    assertThat(flushed.isCompletedExceptionally()).isTrue();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();

    // and a later tick retries and drains
    persistence.onPeriodicTick();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  // ------------------------------------------------------------------
  // Failure and over-capacity handling
  // ------------------------------------------------------------------

  @Test
  void shouldRetryFailedRoundOnNextTick() throws Exception {
    // given a failing first round
    commitBatch(1, 100);
    io.failNextRound();
    persistence.onPeriodicTick();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the failure was counted, nothing reached the durable store, the segments stayed
    assertThat(failureCount()).isEqualTo(1.0);
    assertThat(passThroughGet(1)).isNull();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();

    // when the next tick retries
    persistence.onPeriodicTick();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the retried round drained the same segments
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldNoteOverCapacityDuringRoundAndFollowUpAfterwards() throws Exception {
    // given a round in flight and an over-capacity batch committed during it (the store budget is
    // 256 bytes, the value below exceeds it on its own)
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onPeriodicTick();
    io.awaitRoundEntered();
    commitBigBatch(2);
    persistence.onBatchCommitted();
    assertThat(io.roundsPersisted()).isZero(); // noted, not stacked

    // when the in-flight round completes
    io.releaseRound();
    runProcessorJobsUntil(
        () -> !persistence.roundInFlight() && !layered.defaultDomain().hasBufferedWrites());

    // then a follow-up over-capacity round drained the noted batch
    assertThat(roundCount("overCapacity")).isEqualTo(1.0);
    assertThat(passThroughGet(2)).isNotNull();
  }

  // ------------------------------------------------------------------
  // Owner-thread plumbing
  // ------------------------------------------------------------------

  private void commitBatch(final long key, final long value) {
    context.runInTransaction(() -> put(key, value));
    lastProcessedPosition.incrementAndGet();
  }

  private void commitBigBatch(final long key) {
    context.runInTransaction(
        () -> {
          final DbLong dbKey = new DbLong();
          dbKey.wrapLong(key);
          // many sibling keys so the pinned bytes clearly exceed the 256-byte budget
          for (long i = 0; i < 64; i++) {
            put(key * 1000 + i, i);
          }
          put(key, key);
        });
    lastProcessedPosition.incrementAndGet();
  }

  private void put(final long key, final long value) {
    final DbLong dbKey = new DbLong();
    final DbLong dbValue = new DbLong();
    dbKey.wrapLong(key);
    dbValue.wrapLong(value);
    columnFamily.upsert(dbKey, dbValue);
  }

  private Long ownerGet(final long key) {
    final DbLong dbKey = new DbLong();
    dbKey.wrapLong(key);
    final DbLong value = columnFamily.get(dbKey);
    return value == null ? null : value.getValue();
  }

  /** Reads committed RocksDB only — what a snapshot checkpoint (or restart) would contain. */
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

  /** Runs queued processor jobs (bounded waiting, no sleeps) until the condition holds. */
  private void runProcessorJobsUntil(final java.util.function.BooleanSupplier condition)
      throws InterruptedException {
    final long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (!condition.getAsBoolean()) {
      final long remainingNanos = deadline - System.nanoTime();
      assertThat(remainingNanos)
          .describedAs("timed out waiting for processor jobs to satisfy the condition")
          .isPositive();
      final Runnable job = processorJobs.poll(remainingNanos, TimeUnit.NANOSECONDS);
      if (job != null) {
        job.run();
      }
    }
  }

  private void runOneProcessorJob() throws InterruptedException {
    final Runnable job = processorJobs.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    assertThat(job).describedAs("expected a queued processor job").isNotNull();
    job.run();
  }

  private double inFlightGauge() {
    return inner
        .getMeterRegistry()
        .get("zeebe.db.layered.persist.inflight")
        .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
        .gauge()
        .value();
  }

  private double roundCount(final String trigger) {
    return inner
        .getMeterRegistry()
        .get("zeebe.db.layered.persist.rounds")
        .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
        .tag("trigger", trigger)
        .counter()
        .count();
  }

  private double failureCount() {
    return inner
        .getMeterRegistry()
        .get("zeebe.db.layered.persist.failures")
        .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
        .counter()
        .count();
  }

  /**
   * Stands in for the IO actor: a single IO thread that can be gated (to keep a round in flight
   * deterministically) or told to fail the next round.
   */
  private static final class ControlledIo
      implements LayeredStatePersistence.PersistIo, AutoCloseable {

    private final ExecutorService ioThread =
        Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "layered-persist-io"));
    private final AtomicBoolean failNext = new AtomicBoolean();
    private final AtomicLong persisted = new AtomicLong();
    // consumed (nulled) by the IO thread so only the next round blocks
    private volatile CountDownLatch entered;
    private volatile CountDownLatch gate;
    // retained for the test thread to await/release after the IO thread consumed the fields above
    private volatile CountDownLatch releasableGate;
    private volatile CountDownLatch enteredProbe;

    @Override
    public ActorFuture<Void> persist(final PersistRound round) {
      final CompletableActorFuture<Void> done = new CompletableActorFuture<>();
      ioThread.execute(
          () -> {
            try {
              final CountDownLatch enteredNow = entered;
              if (enteredNow != null) {
                entered = null;
                enteredNow.countDown();
              }
              final CountDownLatch gateNow = gate;
              if (gateNow != null) {
                gate = null;
                if (!gateNow.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                  throw new IllegalStateException("the round's gate was never released");
                }
              }
              if (failNext.getAndSet(false)) {
                throw new RuntimeException("injected persist failure");
              }
              round.persist();
              persisted.incrementAndGet();
              done.complete(null);
            } catch (final Exception e) {
              done.completeExceptionally(e);
            }
          });
      return done;
    }

    /** Blocks the next round inside the persist step until {@link #releaseRound()}. */
    void blockNextRound() {
      entered = new CountDownLatch(1);
      gate = new CountDownLatch(1);
      releasableGate = gate;
      enteredProbe = entered;
    }

    void awaitRoundEntered() throws InterruptedException {
      final CountDownLatch probe = enteredProbe;
      assertThat(probe).describedAs("blockNextRound() must be called first").isNotNull();
      assertThat(probe.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
          .describedAs("the IO thread never entered the persist step")
          .isTrue();
    }

    void releaseRound() {
      releasableGate.countDown();
    }

    void failNextRound() {
      failNext.set(true);
    }

    long roundsPersisted() {
      return persisted.get();
    }

    @Override
    public void close() {
      if (releasableGate != null) {
        releasableGate.countDown();
      }
      ioThread.shutdownNow();
    }
  }
}
