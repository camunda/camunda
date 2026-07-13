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
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.MergeRound;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.ViewPublisher;
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
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The asynchronous persist driver: rounds prepared on the owner thread, drained on an IO thread
 * while the owner keeps committing batches, and completed back on the owner thread; the
 * scheduled-task barrier and the pre-snapshot flush awaiting in-flight rounds by chaining futures;
 * failed rounds staying buffered and being retried. There is no periodic trigger — rounds start on
 * the buffer-pressure ladder, before snapshots and behind the scheduled-task barrier only.
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
  // the controlled monotonic source view freshness is tracked against — no real time in tests
  private final AtomicLong monotonicNanos = new AtomicLong();
  private final LinkedBlockingQueue<Runnable> processorJobs = new LinkedBlockingQueue<>();
  private ControlledIo io;
  private LayeredStatePersistence persistence;

  @BeforeEach
  void setUp() {
    inner = DefaultZeebeDbFactory.defaultFactory().createDb(dbDirectory);
    layered =
        new LayeredZeebeDb<>(
            inner, new LayeredZeebeDbConfig(256, 0, true, 4, Duration.ofSeconds(1)));
    context = layered.layeredContext();
    columnFamily =
        layered.createColumnFamily(ZbColumnFamilies.DEFAULT, context, new DbLong(), new DbLong());
    io = new ControlledIo();
    persistence =
        new LayeredStatePersistence(
            layered, lastProcessedPosition::get, processorJobs::add, io, monotonicNanos::get);
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
    startRoundNow();
    io.awaitRoundEntered();
    assertThat(persistence.roundInFlight()).isTrue();
    assertThat(inFlightGauge()).isEqualTo(1.0);

    // when the owner thread keeps committing and freezing while the round is in flight
    commitBatch(2, 200);
    persistence.tryFreezeForScheduledTask(null);
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

  // ------------------------------------------------------------------
  // View freshness for asynchronous checkers
  // ------------------------------------------------------------------

  @Test
  void shouldReuseFreshViewForPollingChecker() {
    // given a view made fresh at t=0 and a batch committed afterwards
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();
    commitBatch(1, 100);
    assertThat(layered.defaultDomain().hasActiveWrites()).isTrue();

    // when a polling checker prepares while the view is younger than its own period
    monotonicNanos.set(Duration.ofSeconds(30).toNanos() - 1);
    assertThat(persistence.tryFreezeForScheduledTask(Duration.ofSeconds(30))).isTrue();

    // then no freeze happened — the committed batch stays in the active overlay (where
    // overwrites keep deduplicating in place) and the checker reuses the published view
    assertThat(layered.defaultDomain().hasActiveWrites()).isTrue();
  }

  @Test
  void shouldFreezeForPollingCheckerOnceViewIsOlderThanTolerance() {
    // given a view made fresh at t=0 and a batch committed afterwards
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();
    commitBatch(1, 100);

    // when the polling checker prepares once the view's age reached its period
    monotonicNanos.set(Duration.ofSeconds(30).toNanos());
    assertThat(persistence.tryFreezeForScheduledTask(Duration.ofSeconds(30))).isTrue();

    // then the staleness bound is honored: the buffered batch froze into a view-visible segment
    assertThat(layered.defaultDomain().hasActiveWrites()).isFalse();
  }

  @Test
  void shouldAlwaysFreezeForEventDrivenChecker() {
    // given a perfectly fresh view and a batch committed right after it
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();
    commitBatch(1, 100);

    // when an event-driven checker prepares immediately afterwards (no tolerance)
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();

    // then it froze regardless of the view's age — a missed committed entry is a lost wake-up
    assertThat(layered.defaultDomain().hasActiveWrites()).isFalse();
  }

  @Test
  void shouldExposeReHomedSegmentToEventDrivenCheckerAfterFailedRound() throws Exception {
    // given a committed batch captured raw by a failing round on a then-quiescent partition:
    // completion re-homes the raw tip into a pipeline segment and no active writes remain
    commitBatch(1, 100);
    io.failNextRound();
    startRoundNow();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(layered.defaultDomain().hasActiveWrites()).isFalse();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();

    // when an event-driven checker prepares — nothing is active, so no freeze happens
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();

    // then the view it acquires must contain the re-homed segment's entries: a committed timer
    // missing from this scan would derive no next wake-up and never fire — a lost wake-up
    final ViewPublisher views = layered.defaultDomain().viewPublisher();
    final ReadOnlyView view = views.acquireLatest();
    try {
      assertThat(view.get(ZbColumnFamilies.DEFAULT.name(), serializedLong(1)))
          .containsExactly(serializedLong(100));
    } finally {
      views.release(view);
    }
  }

  /** The raw store bytes of a {@link DbLong} key or value, as the layered store holds them. */
  private static byte[] serializedLong(final long value) {
    final DbLong dbLong = new DbLong();
    dbLong.wrapLong(value);
    final byte[] bytes = new byte[dbLong.getLength()];
    dbLong.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }

  @Test
  void shouldFreezeForPollingCheckerWhileNoViewFreshnessIsTrackedYet() {
    // given a committed batch and no freshness barrier ever run (right after recovery)
    commitBatch(1, 100);

    // when the first polling checker prepares
    assertThat(persistence.tryFreezeForScheduledTask(Duration.ofSeconds(30))).isTrue();

    // then it froze: the initial view predates the buffered recovery writes, so it must not be
    // reused no matter how young the persistence driver itself is
    assertThat(layered.defaultDomain().hasActiveWrites()).isFalse();
  }

  @Test
  void shouldReuseFreshViewForPollingCheckerEvenWhileBatchInFlight() throws Exception {
    // given a fresh view and an open batch on the owner thread
    assertThat(persistence.tryFreezeForScheduledTask(null)).isTrue();
    context.getCurrentTransaction().run(() -> put(1, 100));
    assertThat(layered.defaultDomain().batchInFlight()).isTrue();

    // when / then a polling checker may run right away — an open batch never invalidates an
    // already-published view — while an event-driven checker must wait for the batch
    assertThat(persistence.tryFreezeForScheduledTask(Duration.ofSeconds(30))).isTrue();
    assertThat(persistence.tryFreezeForScheduledTask(null)).isFalse();
    context.getCurrentTransaction().commit();
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
    // given a ladder round in flight on the IO thread and a batch committed during it
    recreateWithBatchFillFraction(1.0);
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onBatchCommitted();
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
    startRoundNow();
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

    // and a later flush retries and drains the same segments
    final CompletableActorFuture<Void> retried = new CompletableActorFuture<>();
    persistence.flushForSnapshot(retried);
    runProcessorJobsUntil(retried::isDone);
    retried.join();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  // ------------------------------------------------------------------
  // Snapshot guard: no round may start between the flush and the checkpoint
  // ------------------------------------------------------------------

  @Test
  void shouldDeferLadderRoundsBetweenSnapshotFlushAndCheckpoint() throws Exception {
    // given a completed pre-snapshot flush whose guard is still latched — the RocksDB checkpoint
    // has not happened yet
    recreateWithBatchFillFraction(1.0);
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    final long roundsAfterFlush = io.roundsPersisted();

    // when a batch commits onto the flat-out rung while the guard is latched
    commitBatch(1, 100);
    persistence.onBatchCommitted();

    // then no round starts: its data slices could reach RocksDB without their anchor right
    // before the checkpoint copies it — a torn cut nothing masks after a restore (the shadowing
    // segments are heap-only and do not travel with a snapshot)
    assertThat(persistence.roundInFlight()).isFalse();
    assertThat(io.roundsPersisted()).isEqualTo(roundsAfterFlush);
    assertThat(passThroughGet(1)).isNull();

    // when the checkpoint completed and the guard is released
    persistence.releaseSnapshotGuard();

    // then the deferred trigger re-fires — latched, never dropped — and the round drains
    runProcessorJobsUntil(
        () -> !persistence.roundInFlight() && !layered.defaultDomain().hasBufferedWrites());
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  @Test
  void shouldDeferScheduledTaskRoundWhileSnapshotGuardLatched() throws Exception {
    // given a latched snapshot guard (flush done, checkpoint pending) and a batch committed after
    // the flush
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    commitBatch(1, 100);

    // when a scheduled task prepares while the guard is latched
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();

    // then its drain round is deferred — not dropped — until the guard is released
    assertThat(ready.isDone()).isFalse();
    assertThat(persistence.roundInFlight()).isFalse();
    persistence.releaseSnapshotGuard();
    runProcessorJobsUntil(ready::isDone);
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  @Test
  void shouldReleaseSnapshotGuardWhenFlushFails() throws Exception {
    // given a flush whose own round fails — no checkpoint follows a failed flush
    commitBatch(1, 100);
    io.failNextRound();
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    runProcessorJobsUntil(flushed::isDone);
    assertThat(flushed.isCompletedExceptionally()).isTrue();

    // when a scheduled task prepares afterwards (its drain round is guard-gated)
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();
    runProcessorJobsUntil(ready::isDone);

    // then the guard did not leak: the retried round ran and drained the buffered batch
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  // ------------------------------------------------------------------
  // Replay watermark
  // ------------------------------------------------------------------

  @Test
  void shouldStampWatermarkWithZeroWhileNoPositionWasProcessed() throws Exception {
    // given writes committed before any position was marked processed (e.g. buffered migration
    // writes on a fresh partition during replay) — the position state reports its UNSET
    // sentinel (-1) then
    lastProcessedPosition.set(-1);
    context.runInTransaction(() -> put(1, 100));

    // when the freeze barrier and a persist round run
    persistence.tryFreezeForScheduledTask(null);
    startRoundNow();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the round's anchor — the newest frozen segment watermark — is the honest "nothing
    // processed yet" 0, never the -1 sentinel, and the buffered write still drained
    assertThat(io.lastRoundAnchor()).isZero();
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  // ------------------------------------------------------------------
  // Failure and over-capacity handling
  // ------------------------------------------------------------------

  @Test
  void shouldRetryFailedRoundOnNextTrigger() throws Exception {
    // given a failing first round
    commitBatch(1, 100);
    io.failNextRound();
    startRoundNow();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the failure was counted, nothing reached the durable store, the segments stayed
    assertThat(failureCount()).isEqualTo(1.0);
    assertThat(passThroughGet(1)).isNull();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();

    // when the next trigger retries
    startRoundNow();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());

    // then the retried round drained the same segments
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldNotStartRoundOnBatchCommitWhileNoSizeBudgetIsExceeded() {
    // given a committed batch well below the per-store budget and no total buffered-bytes budget
    // (the default) — the ladder is off: only the interval cadence persists
    commitBatch(1, 100);

    // when the batch-commit hook fires
    persistence.onBatchCommitted();

    // then no round starts and the batch stays buffered for the next interval tick
    assertThat(persistence.roundInFlight()).isFalse();
    assertThat(io.roundsPersisted()).isZero();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();
  }

  @Test
  void shouldStartPacedRoundEarlyAtLadderStartRung() throws Exception {
    // given a buffered-bytes budget one committed batch fills to 80% — on the start rung (70%),
    // below the flat-out rung (90%) — and a pacing budget clearly exceeding the test runtime
    recreateWithBatchFillFraction(0.8);
    commitBatch(1, 100);

    // when the batch-commit hook fires
    persistence.onBatchCommitted();

    // then a start-rung round drains the batch early — paced, not flat-out (a fully progressed
    // drain would still wait against the 48s budget)
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isPositive();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(roundCount("ladder70")).isEqualTo(1.0);
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldExpediteInFlightRoundAtLadderStartRung() throws Exception {
    // given a start-rung round in flight (blocked on the IO thread) and another batch committed
    // during it, putting the buffered bytes back onto the ladder
    recreateWithBatchFillFraction(0.8);
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onBatchCommitted();
    io.awaitRoundEntered();
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isPositive();
    commitBatch(2, 200);

    // when the batch-commit hook fires mid-round
    persistence.onBatchCommitted();

    // then no second round stacks — the in-flight drain is expedited instead, and the noted rung
    // is followed up once the round completed
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isZero();
    io.releaseRound();
    runProcessorJobsUntil(
        () -> !persistence.roundInFlight() && !layered.defaultDomain().hasBufferedWrites());
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
  }

  @Test
  void shouldStartFlatOutRoundAtLadderFlatOutRung() throws Exception {
    // given a buffered-bytes budget one committed batch fills completely (past the 90% rung)
    recreateWithBatchFillFraction(1.0);
    commitBatch(1, 100);

    // when the batch-commit hook fires
    persistence.onBatchCommitted();

    // then a flat-out round drains the batch unpaced: no wait at any progress
    assertThat(io.lastPacer().delayNanos(0.5, System.nanoTime())).isZero();
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(roundCount("ladder90")).isEqualTo(1.0);
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldCountAdmissionPressureAtFullBudget() throws Exception {
    // given a buffered-bytes budget one committed batch fills to 50% only
    recreateWithBatchFillFraction(0.5);
    commitBatch(1, 100);

    // when the batch-commit hook fires below the ladder
    persistence.onBatchCommitted();

    // then nothing is pressed and no round starts
    assertThat(admissionPressureCount()).isZero();
    assertThat(persistence.roundInFlight()).isFalse();

    // when a second batch fills the budget completely — the ladder's top rung, where admission
    // slow-down would engage if a flow-control seam existed
    commitBatch(2, 200);
    persistence.onBatchCommitted();

    // then the pressure is surfaced as a meter (no admission seam exists yet — see the class
    // javadoc of LayeredStatePersistence) while a flat-out round drains the buffer
    assertThat(admissionPressureCount()).isEqualTo(1.0);
    runProcessorJobsUntil(() -> !persistence.roundInFlight());
    assertThat(roundCount("ladder90")).isEqualTo(1.0);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldNoteOverCapacityDuringRoundAndFollowUpAfterwards() throws Exception {
    // given a round in flight and an over-capacity batch committed during it (the store budget is
    // 256 bytes, the batch below exceeds it on its own)
    io.blockNextRound();
    commitBatch(1, 100);
    startRoundNow();
    io.awaitRoundEntered();
    commitBigBatch(2);
    persistence.onBatchCommitted();
    assertThat(io.roundsPersisted()).isZero(); // noted, not stacked

    // when the in-flight round completes
    io.releaseRound();
    runProcessorJobsUntil(
        () -> !persistence.roundInFlight() && !layered.defaultDomain().hasBufferedWrites());

    // then a follow-up round drained the noted batch — per-store over-capacity feeds the
    // ladder's flat-out rung
    assertThat(roundCount("ladder90")).isEqualTo(1.0);
    assertThat(passThroughGet(2)).isNotNull();
  }

  // ------------------------------------------------------------------
  // Paced drains
  // ------------------------------------------------------------------

  @Test
  void shouldAwaitPacedRoundMidSlicesForSnapshotFlush() throws Exception {
    // given a sliced round frozen mid-drain: its first slice committed (partial state durable,
    // no anchor) and the rest blocked on the IO thread
    commitBatch(1, 100);
    commitBatch(2, 200);
    io.blockNextRoundAfterFirstSlice();
    startRoundNow();
    io.awaitRoundEntered();
    assertThat(passThroughGet(1)).isEqualTo(100); // the torn intermediate cut a paced drain
    assertThat(passThroughGet(2)).isNull(); // passes through — partial state, anchor pending

    // when a pre-snapshot flush is requested mid-round
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);

    // then it must not complete on the torn cut — only once the round fully drained
    assertThat(flushed.isDone()).isFalse();
    io.releaseRound();
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldExpeditePacedDrainOnOverCapacityMidRound() throws Exception {
    // given a paced start-rung round in flight (60s pacing window, so the budget clearly exceeds
    // the test runtime) over a store with a tiny 256-byte budget
    recreateWithBatchFillFraction(0.8, 256);
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onBatchCommitted();
    io.awaitRoundEntered();

    // the pacer would make a fully progressed drain wait (60s budget, barely any elapsed)
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isPositive();

    // when a per-store over-capacity batch commits while the round is in flight
    commitBigBatch(2);
    persistence.onBatchCommitted();

    // then the rung is noted AND the in-flight drain is expedited — no wait at any progress
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isZero();
    io.releaseRound();
    runProcessorJobsUntil(
        () -> !persistence.roundInFlight() && !layered.defaultDomain().hasBufferedWrites());
    assertThat(passThroughGet(2)).isNotNull();
  }

  @Test
  void shouldExpediteInFlightPacedRoundForSnapshotFlush() throws Exception {
    // given a paced start-rung round in flight (60s pacing window — the budget clearly exceeds
    // the test runtime, so an unexpedited drain would still be pacing)
    recreateWithBatchFillFraction(0.8);
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onBatchCommitted();
    io.awaitRoundEntered();
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isPositive();

    // when a pre-snapshot flush starts awaiting the in-flight round
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);

    // then the drain is expedited — no wait at any progress — instead of making the snapshot
    // sit out the pacing budget
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isZero();
    io.releaseRound();
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  @Test
  void shouldExpediteInFlightPacedRoundForScheduledTaskBarrier() throws Exception {
    // given a paced start-rung round in flight (60s pacing window)
    recreateWithBatchFillFraction(0.8);
    io.blockNextRound();
    commitBatch(1, 100);
    persistence.onBatchCommitted();
    io.awaitRoundEntered();
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isPositive();

    // when a scheduled task starts awaiting the in-flight round
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();

    // then the drain is expedited — the task must not sit out the pacing budget
    assertThat(io.lastPacer().delayNanos(1.0, System.nanoTime())).isZero();
    io.releaseRound();
    runProcessorJobsUntil(ready::isDone);
    assertThat(passThroughGet(1)).isEqualTo(100);
  }

  // ------------------------------------------------------------------
  // Off-thread pipeline merges
  // ------------------------------------------------------------------

  /** A pipeline segment limit of 1, so the second freeze makes a merge necessary. */
  private void recreateWithSegmentLimit1() {
    recreate(new LayeredZeebeDbConfig(1024 * 1024, 0, true, 1, Duration.ofSeconds(1)));
  }

  @Test
  void shouldMergeOverLimitPipelineOnIoThread() throws Exception {
    // given -- two freezes pushing the pipeline over its segment limit
    recreateWithSegmentLimit1();
    commitBatch(1, 100);
    persistence.tryFreezeForScheduledTask(null);
    assertThat(persistence.mergeInFlight()).isFalse(); // within the limit, nothing to merge
    commitBatch(2, 200);

    // when -- the next freeze occasion detects the over-limit pipeline
    persistence.tryFreezeForScheduledTask(null);
    assertThat(persistence.mergeInFlight()).isTrue();
    runProcessorJobsUntil(() -> !persistence.mergeInFlight());

    // then -- the merge ran on the IO thread, no persist round was involved, and the buffered
    // writes are still visible to the owner and still not durable
    assertThat(io.roundsMerged()).isEqualTo(1);
    assertThat(io.roundsPersisted()).isZero();
    assertThat(ownerGet(1)).isEqualTo(100);
    assertThat(ownerGet(2)).isEqualTo(200);
    assertThat(passThroughGet(1)).isNull();
    assertThat(layered.defaultDomain().hasBufferedWrites()).isTrue();
  }

  @Test
  void shouldDeferRoundWhileMergeInFlight() throws Exception {
    // given -- a merge blocked on the IO thread
    recreateWithSegmentLimit1();
    commitBatch(1, 100);
    persistence.tryFreezeForScheduledTask(null);
    commitBatch(2, 200);
    io.blockNextRound();
    persistence.tryFreezeForScheduledTask(null);
    io.awaitRoundEntered();
    assertThat(persistence.mergeInFlight()).isTrue();

    // when -- a round is requested while the merge is in flight
    final ActorFuture<Void> ready = persistence.prepareForScheduledTask();
    assertThat(ready).isNotNull();

    // then -- no round started (its captured segments could concurrently merge otherwise)
    assertThat(persistence.roundInFlight()).isFalse();
    assertThat(io.roundsPersisted()).isZero();

    // when -- the merge completes, the deferred round drains
    io.releaseRound();
    runProcessorJobsUntil(ready::isDone);

    // then
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  @Test
  void shouldAwaitInFlightMergeBeforeSnapshotFlush() throws Exception {
    // given -- a merge blocked on the IO thread
    recreateWithSegmentLimit1();
    commitBatch(1, 100);
    persistence.tryFreezeForScheduledTask(null);
    commitBatch(2, 200);
    io.blockNextRound();
    persistence.tryFreezeForScheduledTask(null);
    io.awaitRoundEntered();

    // when -- a pre-snapshot flush is requested mid-merge
    final CompletableActorFuture<Void> flushed = new CompletableActorFuture<>();
    persistence.flushForSnapshot(flushed);
    assertThat(flushed.isDone()).isFalse();

    // then -- it chains behind the merge and its own round, and completes with a fresh cut
    io.releaseRound();
    runProcessorJobsUntil(flushed::isDone);
    flushed.join();
    assertThat(passThroughGet(1)).isEqualTo(100);
    assertThat(passThroughGet(2)).isEqualTo(200);
    assertThat(layered.defaultDomain().hasBufferedWrites()).isFalse();
  }

  // ------------------------------------------------------------------
  // Owner-thread plumbing
  // ------------------------------------------------------------------

  /**
   * Rebuilds the fixture with a total buffered-bytes budget sized so that one {@link
   * #commitBatch(long, long)} fills the given fraction of it, and a pacing window clearly exceeding
   * the test runtime (so paced rounds visibly pace). The batch size is measured on the running
   * fixture instead of hard-coding entry-encoding assumptions.
   */
  private void recreateWithBatchFillFraction(final double fraction) {
    recreateWithBatchFillFraction(fraction, 1024 * 1024);
  }

  private void recreateWithBatchFillFraction(final double fraction, final long maxBytesPerStore) {
    commitBatch(999, 0);
    final long batchBytes = layered.defaultDomain().bufferedBytes();
    assertThat(batchBytes).isPositive();
    recreate(
        new LayeredZeebeDbConfig(
            maxBytesPerStore, Math.round(batchBytes / fraction), true, 4, Duration.ofSeconds(60)));
  }

  /**
   * Starts a round over everything buffered right now through the scheduled-task barrier — the
   * fixture's generic on-demand trigger (there is no periodic one); the barrier future completes on
   * the processor queue and is deliberately not awaited here.
   */
  private void startRoundNow() {
    assertThat(persistence.prepareForScheduledTask()).isNotNull();
  }

  /** Tears the default fixture down and rebuilds it over the same directory with {@code config}. */
  private void recreate(final LayeredZeebeDbConfig config) {
    CloseHelper.quietCloseAll(io, layered);
    inner = DefaultZeebeDbFactory.defaultFactory().createDb(dbDirectory);
    layered = new LayeredZeebeDb<>(inner, config);
    context = layered.layeredContext();
    columnFamily =
        layered.createColumnFamily(ZbColumnFamilies.DEFAULT, context, new DbLong(), new DbLong());
    io = new ControlledIo();
    persistence =
        new LayeredStatePersistence(
            layered, lastProcessedPosition::get, processorJobs::add, io, monotonicNanos::get);
  }

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

  private double admissionPressureCount() {
    return inner
        .getMeterRegistry()
        .get("zeebe.db.layered.admission.pressure")
        .tag("domain", LayeredZeebeDb.DEFAULT_DOMAIN_NAME)
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
    private final AtomicBoolean partialFirstSlice = new AtomicBoolean();
    private final AtomicLong persisted = new AtomicLong();
    private final AtomicLong merged = new AtomicLong();
    private final AtomicLong lastRoundAnchor = new AtomicLong(Long.MIN_VALUE);
    private volatile DrainPacer lastPacer;
    // consumed (nulled) by the IO thread so only the next round blocks
    private volatile CountDownLatch entered;
    private volatile CountDownLatch gate;
    // retained for the test thread to await/release after the IO thread consumed the fields above
    private volatile CountDownLatch releasableGate;
    private volatile CountDownLatch enteredProbe;

    @Override
    public ActorFuture<Void> persist(final PersistRound round, final DrainPacer pacer) {
      lastRoundAnchor.set(round.anchor());
      lastPacer = pacer;
      final IoStep preGate =
          partialFirstSlice.getAndSet(false) ? () -> round.persistSlice(1) : null;
      // the post-gate persist() drains from scratch — re-draining over a committed partial slice
      // is idempotent, exactly like a successor's forward completion
      return runOnIoThread(
          preGate,
          () -> {
            round.persist();
            persisted.incrementAndGet();
          });
    }

    @Override
    public ActorFuture<Void> merge(final MergeRound round) {
      return runOnIoThread(
          null,
          () -> {
            round.merge();
            merged.incrementAndGet();
          });
    }

    /** Runs the step on the IO thread, honoring the entered/gate latches and failure injection. */
    private ActorFuture<Void> runOnIoThread(final IoStep preGate, final IoStep step) {
      final CompletableActorFuture<Void> done = new CompletableActorFuture<>();
      ioThread.execute(
          () -> {
            try {
              if (preGate != null) {
                preGate.run();
              }
              final CountDownLatch enteredNow = entered;
              if (enteredNow != null) {
                entered = null;
                enteredNow.countDown();
              }
              final CountDownLatch gateNow = gate;
              if (gateNow != null) {
                gate = null;
                if (!gateNow.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                  throw new IllegalStateException("the step's gate was never released");
                }
              }
              if (failNext.getAndSet(false)) {
                throw new RuntimeException("injected IO failure");
              }
              step.run();
              done.complete(null);
            } catch (final Exception e) {
              done.completeExceptionally(e);
            }
          });
      return done;
    }

    /** Blocks the next IO step (persist or merge) until {@link #releaseRound()}. */
    void blockNextRound() {
      entered = new CountDownLatch(1);
      gate = new CountDownLatch(1);
      releasableGate = gate;
      enteredProbe = entered;
    }

    /**
     * Blocks the next persist like {@link #blockNextRound()}, but only after one committed
     * sub-batch slice — freezing the round in the torn intermediate state a paced drain passes
     * through: partial state durable, no anchor.
     */
    void blockNextRoundAfterFirstSlice() {
      partialFirstSlice.set(true);
      blockNextRound();
    }

    /** The pacer of the round most recently handed to the IO thread. */
    DrainPacer lastPacer() {
      return lastPacer;
    }

    void awaitRoundEntered() throws InterruptedException {
      final CountDownLatch probe = enteredProbe;
      assertThat(probe).describedAs("blockNextRound() must be called first").isNotNull();
      assertThat(probe.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
          .describedAs("the IO thread never entered the blocked step")
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

    long roundsMerged() {
      return merged.get();
    }

    /** The anchor (newest frozen watermark) of the round most recently handed to the IO thread. */
    long lastRoundAnchor() {
      return lastRoundAnchor.get();
    }

    @Override
    public void close() {
      if (releasableGate != null) {
        releasableGate.countDown();
      }
      ioThread.shutdownNow();
    }

    @FunctionalInterface
    private interface IoStep {
      void run() throws Exception;
    }
  }
}
