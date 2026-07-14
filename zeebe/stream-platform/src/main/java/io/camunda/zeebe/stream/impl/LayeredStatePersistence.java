/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.MergeRound;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.PersistTrigger;
import io.camunda.zeebe.db.layered.zdb.LayeredDomain;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Drives the memory- and snapshot-triggered persist rounds and the on-demand freezes of a {@link
 * LayeredZeebeDb}'s engine domain (experimental; only active when the layered-state flag is on).
 * Rounds are prepared and completed on the stream processor's actor, but their drain-and-commit
 * step runs on a dedicated IO executor (see {@link PersistIo}) so processing continues while
 * buffered state moves to RocksDB.
 *
 * <p><b>There is deliberately no periodic persist cadence.</b> Recovery after a crash restores from
 * snapshots — the intermediate persists write a runtime directory a crash discards — so an
 * age-based cadence bounds nothing an invariant needs. What actually needs a round is exactly what
 * triggers one: buffered memory (the pressure ladder below, which also bounds the in-place takeover
 * replay window), a snapshot about to checkpoint, and a sync scheduled task about to read persisted
 * state. A start-rung round's drain is <em>paced</em>: it commits sub-batch slices spread across
 * the configured pacing window (see {@link DrainPacer}) instead of dumping one large batch — the
 * deadline only shapes disk amplitude, correctness never depends on it — while flat-out-rung,
 * pre-snapshot and scheduled-task rounds drain unpaced (their completion is what a consumer is
 * waiting for), and a rung observed mid-round expedites the pacer:
 *
 * <ul>
 *   <li>{@link #onBatchCommitted()} — invoked after every committed processing batch; reacts to
 *       buffer pressure on a graduated ladder (see {@link #onBatchCommitted()}): a paced round
 *       starts early at the configured start rung, an unpaced round at the flat-out rung (which a
 *       single store over its own budget feeds too), and reaching the full budget is surfaced as
 *       admission pressure. While a round is in flight a rung only expedites it — rounds never
 *       stack — and is re-checked once the round completed.
 *   <li>{@link #flushForSnapshot(CompletableActorFuture)} — invoked before a snapshot checkpoint;
 *       completes once the durable store holds a cut at least as new as the position the snapshot
 *       will claim, awaiting any in-flight round first. It also latches a snapshot guard that
 *       defers every new round start until {@link #releaseSnapshotGuard()} after the checkpoint — a
 *       round started in between could commit data slices without their anchor into the very
 *       RocksDB state the checkpoint copies (see the method javadoc).
 *   <li>{@link #prepareForScheduledTask()} — invoked before a scheduled task executes on the stream
 *       processor's actor; returns a future after which the task's persisted-state reads observe
 *       everything committed before the preparation began.
 *   <li>{@link #tryFreezeForScheduledTask(Duration)} — invoked before an asynchronous checker
 *       executes: event-driven checkers get a freeze, so the view they acquire observes every batch
 *       committed before their execution; polling checkers reuse the current published view while
 *       it is younger than their own period.
 * </ul>
 *
 * <p><b>Freezes are on-demand only — there is deliberately no periodic freeze cadence.</b> The only
 * consumers of view freshness are the asynchronous checkers, and they self-freshen through the
 * pre-execution barrier above; the query API reads pass-through state and is deprecated besides.
 * Freezing any earlier than someone needs the freshness is strictly worse: an overwrite landing in
 * the active overlay deduplicates in place for free, while the same overwrite landing after a
 * freeze creates a cross-segment version that only a pipeline merge can collapse. Every freeze
 * occasion checks whether a pipeline grew past its segment limit and, if so, hands a merge round to
 * the IO executor — pipeline merges never run on the processing actor. A merge may overlap an
 * in-flight persist round (it captures only non-persisting segments, and the IO executor serializes
 * the two), but a new round is deferred while a merge is in flight — segments captured by a round
 * must never concurrently merge.
 *
 * <p><b>Threading and liveness:</b> every method runs on the stream processor's actor; only the
 * prepared round's {@link PersistRound#persist()} runs on the IO executor, and its completion is
 * marshalled back onto the actor through {@code onComplete(..., processor)}. Nothing ever blocks
 * the actor thread — waiting for an in-flight round always happens by chaining futures — and the IO
 * executor never calls back into this class except by completing the persist future, so no wait
 * cycle between the two threads exists. Rounds are single-flight (enforced here and by the
 * coordinator); a failed round completes with {@code success=false} on the actor, its segments stay
 * buffered (library guarantee), and the next trigger retries.
 *
 * <p>Neither rounds nor freezes <em>start</em> while a batch is in flight: a mid-batch cut would
 * have to include uncommitted staging writes. Completing a round while a batch is in flight is fine
 * (completion never touches staging contents). The pre-snapshot and pre-scheduled-task preparations
 * re-check after the batch completed by re-submitting themselves.
 */
final class LayeredStatePersistence {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final LayeredDomain domain;
  private final LongSupplier lastProcessedPosition;
  private final Executor processor;
  private final PersistIo io;
  private final LongSupplier monotonicNanos;
  private final long maxBufferedBytes;
  // the buffer-pressure ladder rungs, precomputed from the configured fractions of
  // maxBufferedBytes (zero when no budget is configured — the ladder is off then); at least one
  // byte so an empty buffer can never sit on a rung
  private final long ladderStartBytes;
  private final long ladderFlatOutBytes;
  // the pacing budget of a start-rung round's sliced drain: the configured pacing window
  private final long pacingBudgetNanos;

  /**
   * The completion of the single in-flight round, or null while none is; completes on the processor
   * actor — after the round was completed on the domain — with null on success or the round's
   * failure cause. Never completes exceptionally.
   */
  private CompletableActorFuture<Throwable> inFlightRound;

  /**
   * The completion of the single in-flight merge, or null while none is; completes on the processor
   * actor — after the merge was completed on the domain — and never exceptionally (a failed merge
   * is retried by the next freeze occasion).
   */
  private CompletableActorFuture<Void> inFlightMerge;

  /** A ladder rung observed while a round or merge was in flight; re-checked on completion. */
  private boolean sizeTriggerWhileInFlight;

  /**
   * How many snapshot guards are currently latched (see {@link #flushForSnapshot}): from a
   * pre-snapshot flush's acceptance until the snapshot's RocksDB checkpoint completed, no new
   * persist round may start — a paced round commits data slices before the anchor, and a checkpoint
   * taken between two slices would capture state ahead of its anchor with nothing masking the tear
   * (the shadowing segments are heap-only and do not travel with a snapshot). Rounds are deferred,
   * never dropped: latched triggers re-fire on release.
   */
  private int snapshotGuards;

  /**
   * Completes when the last snapshot guard is released, so deferred round starts can chain on it;
   * non-null exactly while {@link #snapshotGuards} is positive. Never completes exceptionally.
   */
  private CompletableActorFuture<Void> snapshotGuardReleased;

  /** A ladder rung observed while the snapshot guard was latched; re-checked on release. */
  private boolean sizeTriggerWhileGuarded;

  /**
   * Whether the full-budget admission warning was already logged for the current excursion above
   * the budget; re-armed once the buffered bytes drop below it again (the meter counts every
   * pressed batch boundary, the log only the excursion).
   */
  private boolean admissionPressureWarned;

  /**
   * The pacer of the single in-flight round, or null while none is — the owner's urgency hook: a
   * size trigger observed mid-round {@link DrainPacer#expedite() expedites} it, so the drain stops
   * pacing and finishes flat-out while memory is under pressure.
   */
  private DrainPacer inFlightPacer;

  /**
   * The {@link #monotonicNanos} instant up to which the published read views are known fresh: every
   * batch committed before it is view-visible. Advanced by {@link
   * #tryFreezeForScheduledTask(Duration)} after it froze (or found nothing to freeze) —
   * single-threaded on the processor actor, so no commit can slip between the freeze and the stamp.
   * Meaningful only while {@link #viewFreshnessTracked} is true.
   */
  private long viewFreshAsOfNanos;

  private boolean viewFreshnessTracked;

  /**
   * @param db the layered database the stream processor writes through
   * @param lastProcessedPosition supplies the watermark of a round: the highest log position whose
   *     effects the buffered state contains (the last successfully committed position)
   * @param processor appends a runnable to the <em>end</em> of the stream processor actor's job
   *     queue (pass {@code actor::submit}); end-of-queue semantics are load-bearing — the
   *     batch-in-flight re-checks must yield to the jobs completing the batch, or they would starve
   *     it
   * @param io runs the persist step of prepared rounds off the processor actor
   * @param monotonicNanos the monotonic time source view freshness is tracked against (the actor
   *     clock's nanos in production, a controlled source in tests); never used as wall-clock time
   */
  LayeredStatePersistence(
      final LayeredZeebeDb<?> db,
      final LongSupplier lastProcessedPosition,
      final Executor processor,
      final PersistIo io,
      final LongSupplier monotonicNanos) {
    this.lastProcessedPosition =
        Objects.requireNonNull(lastProcessedPosition, "lastProcessedPosition");
    this.processor = Objects.requireNonNull(processor, "processor");
    this.io = Objects.requireNonNull(io, "io");
    this.monotonicNanos = Objects.requireNonNull(monotonicNanos, "monotonicNanos");
    domain = db.defaultDomain();
    maxBufferedBytes = db.config().maxBufferedBytes();
    ladderStartBytes = ladderRungBytes(db.config().ladderStartFraction());
    ladderFlatOutBytes = ladderRungBytes(db.config().ladderFlatOutFraction());
    pacingBudgetNanos = db.config().persistPacingWindow().toNanos();
    // build the coordinator eagerly: every layered column family must exist by now (they are all
    // created during recovery and record-processor init), and failing fast here beats failing on
    // the first persist round
    domain.coordinator();
  }

  /** A ladder rung in bytes — at least one, so an empty buffer can never sit on a rung. */
  private long ladderRungBytes(final double fraction) {
    return maxBufferedBytes <= 0 ? 0 : Math.max(1, (long) (maxBufferedBytes * fraction));
  }

  /** Whether a persist round is currently in flight on the IO executor. */
  boolean roundInFlight() {
    return inFlightRound != null;
  }

  /** Whether a pipeline merge is currently in flight on the IO executor. */
  boolean mergeInFlight() {
    return inFlightMerge != null;
  }

  /**
   * The watermark for freezes and persist rounds: the last successfully processed position, clamped
   * to 0 while none exists yet. During replay the position state is fed by the replayed batches
   * themselves, so it is already the correct source — but before the first batch marks a position
   * (e.g. buffered migration writes on a fresh partition) it reports its UNSET sentinel (-1), which
   * must not leak into segment watermarks or round anchors; 0 is the honest "nothing processed yet"
   * stamp (log positions start above it) and keeps watermarks monotonic.
   */
  private long watermark() {
    return Math.max(0, lastProcessedPosition.getAsLong());
  }

  /**
   * Prepares view freshness right before an asynchronous checker executes, split by the checker's
   * semantics:
   *
   * <ul>
   *   <li><b>Event-driven checkers</b> ({@code toleratedStaleness} null) get an unconditional
   *       freeze of the active overlays, so the read view they acquire observes every batch
   *       committed before their execution — the freshness that keeps them from losing wake-ups (a
   *       scan that misses a committed-but-unfrozen timer could otherwise derive no next wake-up
   *       and never run again).
   *   <li><b>Polling checkers</b> (a positive tolerance — their own polling period) reuse the
   *       current published view while it is younger than the tolerance: they rescan their full
   *       range every period, so an entry missed by one poll is picked up by the next, and the
   *       observed staleness stays below one period. Every reused view is a freeze avoided, which
   *       keeps overwrites deduplicating in place in the active overlay. The reuse check runs
   *       before the batch-in-flight check on purpose — an open batch never invalidates an
   *       already-published view.
   * </ul>
   *
   * <p>This barrier and the implicit freeze of a preparing persist round are the only freeze
   * sources (see the class javadoc for why no cadence exists); after a freeze (or when nothing
   * needed freezing) it also runs the post-freeze merge check — a merge deferred while another was
   * in flight must be retried even when nothing new froze. Polling reuses skip the merge check;
   * freeze occasions still occur at least once per polling period.
   *
   * @param toleratedStaleness the checker's staleness tolerance (its own period), or null for
   *     event-driven checkers that must observe every committed batch
   * @return false if a batch is in flight and a freeze was needed — staging writes must never
   *     surface, so the caller retries once the batch completed (matching today's semantics, where
   *     a checker's scan sees committed batches only)
   */
  boolean tryFreezeForScheduledTask(final @Nullable Duration toleratedStaleness) {
    if (toleratedStaleness != null
        && viewFreshnessTracked
        && monotonicNanos.getAsLong() - viewFreshAsOfNanos < toleratedStaleness.toNanos()) {
      return true;
    }
    if (domain.batchInFlight()) {
      return false;
    }
    if (domain.hasActiveWrites()) {
      domain.freezeNow(watermark());
    }
    // stamp after the freeze: this method runs single-threaded on the processor actor, so no
    // batch can commit between the freeze and the stamp — everything committed before this
    // instant is now view-visible
    viewFreshAsOfNanos = monotonicNanos.getAsLong();
    viewFreshnessTracked = true;
    maybeStartMerge();
    return true;
  }

  /**
   * Makes every batch committed before this call visible to a scheduled task about to execute on
   * the stream processor's actor. Event-driven checkers (timers, job backoff) re-derive their next
   * wake-up from persisted-state scans; a scan that misses a committed-but-buffered entry would
   * lose that wake-up permanently, so the buffer is drained before the task runs.
   *
   * <p>One preparation makes one drain attempt: it awaits an in-flight round first (its cut may
   * predate this call), then runs at most one round of its own over everything still buffered.
   * Batches committed <em>while</em> that round drained are deliberately not chased — a preparation
   * that re-drained until the buffer was empty could starve the task under sustained load, and
   * entries committed after the preparation began re-trigger their checkers through the engine's
   * own reschedule notifications, exactly as they do when they commit after the task ran.
   *
   * @return null when the task may run right now (nothing buffered, no round in flight), otherwise
   *     a future completing — never exceptionally — once the drain attempt finished. A failed round
   *     keeps its segments buffered and the task still runs: a stale scan is preferable to blocking
   *     all scheduled work while persistence is failing (the next trigger retries).
   */
  @Nullable ActorFuture<Void> prepareForScheduledTask() {
    if (!domain.batchInFlight() && !roundInFlight() && !domain.hasBufferedWrites()) {
      return null;
    }
    final CompletableActorFuture<Void> ready = new CompletableActorFuture<>();
    continueScheduledTaskPreparation(ready, false);
    return ready;
  }

  private void continueScheduledTaskPreparation(
      final CompletableActorFuture<Void> ready, final boolean roundRan) {
    if (domain.batchInFlight()) {
      // staging writes must never surface; re-check once the batch completed
      processor.execute(() -> continueScheduledTaskPreparation(ready, roundRan));
      return;
    }
    if (mergeInFlight()) {
      // a round must not start while a merge is in flight; merges are index-only and quick
      inFlightMerge.onComplete(
          (ignored, error) -> continueScheduledTaskPreparation(ready, roundRan), processor);
      return;
    }
    if (roundInFlight()) {
      // await the round already in flight before (or instead of) starting our own — expedited:
      // the task is waiting on its completion, so a paced drain must stop pacing now instead of
      // sitting out a wait that can span most of the pacing window
      expediteInFlightRound();
      inFlightRound.onComplete(
          (failure, ignored) -> continueScheduledTaskPreparation(ready, roundRan), processor);
      return;
    }
    if (roundRan || !domain.hasBufferedWrites()) {
      // drained — or this preparation's own round failed, in which case the segments stayed
      // buffered and the task runs on a stale scan rather than blocking on failing persistence
      ready.complete(null);
      return;
    }
    if (snapshotGuardHeld()) {
      // rounds are latched while a snapshot is between its flush and its checkpoint (see
      // onBatchCommitted); deferred, not dropped — the preparation resumes on release
      snapshotGuardReleased.onComplete(
          (ignored, error) -> continueScheduledTaskPreparation(ready, roundRan), processor);
      return;
    }
    startRound(PersistTrigger.SCHEDULED_TASK)
        .onComplete((failure, ignored) -> continueScheduledTaskPreparation(ready, true), processor);
  }

  /**
   * Reacts to buffer pressure right after a batch commit, on a graduated ladder over the domain's
   * buffered bytes — which include the bytes captured by an in-flight round until it completes, so
   * pressure never under-reads while a drain runs:
   *
   * <ul>
   *   <li><b>Start rung</b> (configured fraction of {@code maxBufferedBytes}, default 70%): a paced
   *       round starts early, spreading the drain's IO while pressure is still moderate.
   *   <li><b>Flat-out rung</b> (default 90%): a round starts immediately and drains unpaced; a
   *       single store over its own byte budget ({@code overCapacity}) feeds this rung too.
   *   <li><b>Full budget</b> (100%): admission slow-down would engage here, but no clean seam into
   *       the log stream's flow control exists yet (its write-rate limiter is config-owned and fed
   *       by the exporting backlog only, and rejecting appends needs a per-context decision inside
   *       {@code FlowControl}) — so the pressure is surfaced instead: counted on every pressed
   *       batch boundary and warned once per excursion above the budget.
   * </ul>
   *
   * <p>Rounds never stack: a rung observed while a round (or merge) is in flight expedites the
   * in-flight drain via its pacer — memory pressure beats checkpoint spreading — and is re-checked
   * once the round completed.
   */
  void onBatchCommitted() {
    noteAdmissionPressure();
    final PersistTrigger rung = ladderRung();
    if (rung == null) {
      return;
    }
    if (roundInFlight() || mergeInFlight()) {
      sizeTriggerWhileInFlight = true;
      // on a rung while a round is already draining: stop pacing it, so the drain finishes
      // flat-out and the follow-up round starts sooner
      expediteInFlightRound();
      return;
    }
    if (snapshotGuardHeld()) {
      // a snapshot is between its buffered-state flush and its RocksDB checkpoint: a round
      // starting now could commit data slices the checkpoint captures without their anchor (the
      // heap-only masking segments do not travel with a snapshot), so the rung is latched and
      // re-checked once the guard is released
      sizeTriggerWhileGuarded = true;
      return;
    }
    if (domain.batchInFlight()) {
      return;
    }
    startRound(rung);
  }

  /** Expedites the pacer of the in-flight round, if any — a no-op otherwise. */
  private void expediteInFlightRound() {
    if (inFlightPacer != null) {
      inFlightPacer.expedite();
    }
  }

  /** Meters (each occasion) and warns (once per excursion) when the full budget is reached. */
  private void noteAdmissionPressure() {
    if (maxBufferedBytes <= 0 || domain.bufferedBytes() < maxBufferedBytes) {
      admissionPressureWarned = false;
      return;
    }
    domain.countAdmissionPressure();
    if (!admissionPressureWarned) {
      admissionPressureWarned = true;
      LOG.warn(
          "The buffered layered state reached its full budget of {} bytes; processing continues"
              + " while the drain catches up flat-out, but no admission slow-down engages yet"
              + " (no flow-control seam)",
          maxBufferedBytes);
    }
  }

  /** The ladder rung the buffered state sits on right now, or null while it is below the ladder. */
  private @Nullable PersistTrigger ladderRung() {
    if (domain.overCapacity()) {
      return PersistTrigger.LADDER_90;
    }
    if (ladderFlatOutBytes <= 0) {
      return null;
    }
    final long buffered = domain.bufferedBytes();
    if (buffered >= ladderFlatOutBytes) {
      return PersistTrigger.LADDER_90;
    }
    if (buffered >= ladderStartBytes) {
      return PersistTrigger.LADDER_70;
    }
    return null;
  }

  /**
   * Drains the buffered state so a subsequent snapshot checkpoints a durable cut at least as new as
   * the last-processed position resolved before this flush was requested. Awaits an in-flight round
   * first and re-evaluates afterwards — batches committed while that round drained buffered new
   * writes (including the recovery anchor), so its cut alone cannot be assumed fresh enough. At
   * most one own pre-snapshot round follows: it drains everything buffered at its prepare, which
   * covers every position resolved before the flush; batches committed during it belong to later
   * snapshots.
   *
   * <p><b>Snapshot guard:</b> accepting the flush latches a guard that defers every new round start
   * until {@link #releaseSnapshotGuard()} — a paced round commits data slices before the anchor,
   * and a RocksDB checkpoint taken between two slices would capture state ahead of its anchor with
   * nothing masking the tear once restored (the shadowing segments are heap-only), so no round may
   * start between this flush and the snapshot's checkpoint. On success the guard stays latched and
   * the caller must release it once the checkpoint completed (or failed); a failed flush releases
   * the guard itself before completing exceptionally — no checkpoint follows then.
   *
   * @param flushed completed once the durable cut is fresh enough, or exceptionally if the
   *     pre-snapshot round fails — the snapshot must not proceed on a stale durable cut
   */
  void flushForSnapshot(final CompletableActorFuture<Void> flushed) {
    acquireSnapshotGuard();
    continueSnapshotFlush(flushed);
  }

  private void continueSnapshotFlush(final CompletableActorFuture<Void> flushed) {
    if (domain.batchInFlight()) {
      // a batch is mid-flight on the processor actor; run again once it completed
      processor.execute(() -> continueSnapshotFlush(flushed));
      return;
    }
    if (mergeInFlight()) {
      // the flush's round must not start while a merge is in flight; merges are quick
      inFlightMerge.onComplete((ignored, error) -> continueSnapshotFlush(flushed), processor);
      return;
    }
    if (roundInFlight()) {
      // the snapshot is waiting on the round's completion, so a paced drain must stop pacing
      // now instead of sitting out a wait that can span most of the pacing window
      expediteInFlightRound();
      inFlightRound.onComplete((failure, ignored) -> continueSnapshotFlush(flushed), processor);
      return;
    }
    if (!domain.hasBufferedWrites()) {
      flushed.complete(null);
      return;
    }
    startRound(PersistTrigger.PRE_SNAPSHOT)
        .onComplete(
            (failure, ignored) -> {
              if (failure == null) {
                flushed.complete(null);
              } else {
                // no checkpoint follows a failed flush, so the guard must not stay latched
                releaseSnapshotGuard();
                flushed.completeExceptionally(
                    new ZeebeDbException(
                        "Failed to persist the buffered layered state before a snapshot; the"
                            + " snapshot must not proceed on a stale durable cut",
                        failure));
              }
            },
            processor);
  }

  /** Whether a snapshot guard is latched — no new round may start while one is. */
  private boolean snapshotGuardHeld() {
    return snapshotGuards > 0;
  }

  private void acquireSnapshotGuard() {
    snapshotGuards++;
    if (snapshotGuardReleased == null) {
      snapshotGuardReleased = new CompletableActorFuture<>();
    }
  }

  /**
   * Releases the snapshot guard latched by {@link #flushForSnapshot} once the snapshot's RocksDB
   * checkpoint completed — successfully or not; the guard must never outlive the checkpoint
   * attempt, or rounds would stay latched forever. Deferred round triggers re-fire now. Processor
   * actor only; a release without a latched guard is a no-op (the failure path may have released
   * already).
   */
  void releaseSnapshotGuard() {
    if (snapshotGuards == 0) {
      return;
    }
    snapshotGuards--;
    if (snapshotGuards > 0) {
      return;
    }
    final CompletableActorFuture<Void> released = snapshotGuardReleased;
    snapshotGuardReleased = null;
    released.complete(null);
    if (sizeTriggerWhileGuarded) {
      sizeTriggerWhileGuarded = false;
      processor.execute(this::onBatchCommitted);
    }
  }

  /**
   * Prepares a round on the processor actor, hands its persist step to the IO executor and marshals
   * the completion back onto the actor. The returned future completes on the actor — never
   * exceptionally — with null on success or the round's failure cause, strictly after the round was
   * completed on the domain, so continuations observe the retired (or retained) segments
   * consistently.
   */
  private ActorFuture<Throwable> startRound(final PersistTrigger trigger) {
    final PersistRound round = domain.preparePersist(watermark(), trigger);
    final CompletableActorFuture<Throwable> completed = new CompletableActorFuture<>();
    inFlightRound = completed;
    // only start-rung rounds pace: a flat-out round drains under memory pressure, and the
    // pre-snapshot and scheduled-task rounds have a consumer waiting on their completion
    final long budgetNanos = trigger == PersistTrigger.LADDER_70 ? pacingBudgetNanos : 0;
    final DrainPacer pacer = new DrainPacer(budgetNanos, System.nanoTime());
    inFlightPacer = pacer;
    io.persist(round, pacer)
        .onComplete(
            (ignored, error) -> {
              final boolean success = error == null;
              domain.completePersist(round, success);
              inFlightRound = null;
              inFlightPacer = null;
              if (!success) {
                // recoverable by design: the segments stay in their pipelines and the next round
                // retries them, while the log remains the durable source of everything buffered
                LOG.warn(
                    "Failed to persist buffered layered state (trigger: {}); will retry",
                    trigger,
                    error);
              }
              final boolean recheckSizeTrigger = sizeTriggerWhileInFlight && success;
              sizeTriggerWhileInFlight = false;
              completed.complete(error);
              if (recheckSizeTrigger) {
                // after a failed round the next batch commit retries anyway — only a successful
                // round needs the deferred rung re-check
                processor.execute(this::onBatchCommitted);
              }
            },
            processor);
    return completed;
  }

  /**
   * Hands a merge round to the IO executor when any of the domain's pipelines grew past its segment
   * limit; a no-op while a merge is already in flight (single-flight — the next freeze occasion
   * retries) or when no store needs merging. The merge may overlap an in-flight persist round: it
   * captures only non-persisting segments, and the shared IO executor serializes the actual work.
   * Completion is marshalled back onto the processor actor, where the merged segments are swapped
   * in; a failed merge leaves the captured runs untouched and is retried by the next freeze
   * occasion.
   */
  private void maybeStartMerge() {
    if (mergeInFlight()) {
      return;
    }
    final MergeRound round = domain.prepareMerge();
    if (round == null) {
      return;
    }
    final CompletableActorFuture<Void> completed = new CompletableActorFuture<>();
    inFlightMerge = completed;
    io.merge(round)
        .onComplete(
            (ignored, error) -> {
              final boolean success = error == null;
              domain.completeMerge(round, success);
              inFlightMerge = null;
              if (!success) {
                LOG.warn(
                    "Failed to merge buffered layered pipeline segments; the captured runs stay"
                        + " unmerged and the next freeze occasion retries",
                    error);
              }
              final boolean recheckSizeTrigger = sizeTriggerWhileInFlight;
              sizeTriggerWhileInFlight = false;
              completed.complete(null);
              if (recheckSizeTrigger) {
                // a size-triggered round was deferred while this merge ran; re-check now
                processor.execute(this::onBatchCommitted);
              }
            },
            processor);
  }

  /** Runs the persist and merge steps of prepared rounds off the processor actor. */
  interface PersistIo {

    /**
     * Runs the round's drain on an IO thread — paced against the given pacer, in sub-batch slices
     * (see {@link LayeredPersistIoActor}); the returned future completes once the final
     * (anchor-carrying) slice committed, and exceptionally when the drain failed or never ran.
     */
    ActorFuture<Void> persist(PersistRound round, DrainPacer pacer);

    /**
     * Runs {@link MergeRound#merge()} on an IO thread; the returned future completes exceptionally
     * when the merge failed or never ran. Must be serialized with {@link #persist(PersistRound)}
     * (one executor thread per domain), so a merge overlapping a round never executes concurrently
     * with it.
     */
    ActorFuture<Void> merge(MergeRound round);
  }
}
