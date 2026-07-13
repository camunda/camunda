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
 * Drives the persist cadence and the on-demand freezes of a {@link LayeredZeebeDb}'s engine domain
 * (experimental; only active when the layered-state flag is on). Rounds are prepared and completed
 * on the stream processor's actor, but their drain-and-commit step runs on a dedicated IO executor
 * (see {@link PersistIo}) so processing continues while buffered state moves to RocksDB. The drain
 * is <em>paced</em>: it commits sub-batch slices spread across {@code pacingTargetFraction ×
 * persistInterval} (see {@link DrainPacer}), smoothing the IO instead of dumping one large batch —
 * and a size trigger observed mid-round expedites the pacer, so the drain finishes flat-out while
 * memory is under pressure:
 *
 * <ul>
 *   <li>{@link #onPeriodicTick()} — the regular persist cadence, invoked at the configured persist
 *       interval; starts a round whenever anything is buffered and no round is in flight.
 *   <li>{@link #onBatchCommitted()} — invoked after every committed processing batch; starts a
 *       round as soon as a size budget is exceeded (a single store over its own budget, or the
 *       domain's total buffered bytes over the configured {@code maxBufferedBytes}). While a round
 *       is in flight the trigger is only noted — rounds never stack — and re-checked once the round
 *       completed.
 *   <li>{@link #flushForSnapshot(CompletableActorFuture)} — invoked before a snapshot checkpoint;
 *       completes once the durable store holds a cut at least as new as the position the snapshot
 *       will claim, awaiting any in-flight round first.
 *   <li>{@link #prepareForScheduledTask()} — invoked before a scheduled task executes on the stream
 *       processor's actor; returns a future after which the task's persisted-state reads observe
 *       everything committed before the preparation began.
 *   <li>{@link #tryFreezeForScheduledTask()} — invoked before an asynchronous checker executes, so
 *       the view it acquires observes every batch committed before its execution.
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
 * (completion never touches staging contents). Periodic ticks simply skip and retry on their next
 * occasion; the pre-snapshot and pre-scheduled-task preparations re-check after the batch completed
 * by re-submitting themselves.
 */
final class LayeredStatePersistence {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final LayeredDomain domain;
  private final LongSupplier lastProcessedPosition;
  private final Executor processor;
  private final PersistIo io;
  private final Duration persistInterval;
  private final long maxBufferedBytes;
  // the pacing budget of one round's sliced drain: pacingTargetFraction × persistInterval, so a
  // paced round normally finishes before the next interval trigger would fire
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

  /** A size trigger observed while a round or merge was in flight; re-checked on completion. */
  private boolean sizeTriggerWhileInFlight;

  /**
   * The pacer of the single in-flight round, or null while none is — the owner's urgency hook: a
   * size trigger observed mid-round {@link DrainPacer#expedite() expedites} it, so the drain stops
   * pacing and finishes flat-out while memory is under pressure.
   */
  private DrainPacer inFlightPacer;

  /**
   * @param db the layered database the stream processor writes through
   * @param lastProcessedPosition supplies the watermark of a round: the highest log position whose
   *     effects the buffered state contains (the last successfully committed position)
   * @param processor appends a runnable to the <em>end</em> of the stream processor actor's job
   *     queue (pass {@code actor::submit}); end-of-queue semantics are load-bearing — the
   *     batch-in-flight re-checks must yield to the jobs completing the batch, or they would starve
   *     it
   * @param io runs the persist step of prepared rounds off the processor actor
   */
  LayeredStatePersistence(
      final LayeredZeebeDb<?> db,
      final LongSupplier lastProcessedPosition,
      final Executor processor,
      final PersistIo io) {
    this.lastProcessedPosition =
        Objects.requireNonNull(lastProcessedPosition, "lastProcessedPosition");
    this.processor = Objects.requireNonNull(processor, "processor");
    this.io = Objects.requireNonNull(io, "io");
    domain = db.defaultDomain();
    persistInterval = db.config().persistInterval();
    maxBufferedBytes = db.config().maxBufferedBytes();
    pacingBudgetNanos =
        (long) (persistInterval.toNanos() * db.config().persistPacingTargetFraction());
    // build the coordinator eagerly: every layered column family must exist by now (they are all
    // created during recovery and record-processor init), and failing fast here beats failing on
    // the first persist round
    domain.coordinator();
  }

  Duration persistInterval() {
    return persistInterval;
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

  /** The regular persist cadence; a failed round stays buffered and the next tick retries. */
  void onPeriodicTick() {
    if (roundInFlight()
        || mergeInFlight()
        || domain.batchInFlight()
        || !domain.hasBufferedWrites()) {
      return;
    }
    startRound(PersistTrigger.INTERVAL);
  }

  /**
   * Freezes the active overlays right before an asynchronous checker executes, so the read view the
   * checker acquires observes every batch committed before its execution — the freshness that keeps
   * event-driven checkers from losing wake-ups (a scan that misses a committed-but-unfrozen timer
   * could otherwise derive no next wake-up and never run again). This barrier and the implicit
   * freeze of a preparing persist round are the only freeze sources (see the class javadoc for why
   * no cadence exists); it also runs the post-freeze merge check, whether it froze or not — a merge
   * deferred while another was in flight must be retried even when nothing new froze.
   *
   * @return false if a batch is in flight — its staging writes must never surface, so the caller
   *     retries once the batch completed (matching today's semantics, where a checker's scan sees
   *     committed batches only)
   */
  boolean tryFreezeForScheduledTask() {
    if (domain.batchInFlight()) {
      return false;
    }
    if (domain.hasActiveWrites()) {
      domain.freezeNow(watermark());
    }
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
   *     all scheduled work while persistence is failing (the interval cadence retries).
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
      // await the round already in flight before (or instead of) starting our own
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
    startRound(PersistTrigger.SCHEDULED_TASK)
        .onComplete((failure, ignored) -> continueScheduledTaskPreparation(ready, true), processor);
  }

  /**
   * Notes a size condition right after a batch commit and starts a round for it once none is in
   * flight; triggers observed mid-round are re-checked on completion instead of stacking a second
   * round. Two size conditions exist: a single store over its own byte budget, and — when a {@code
   * maxBufferedBytes} budget is configured — the domain's total buffered bytes reaching it, which
   * bounds memory and the recovery replay window by size independently of the persist interval
   * (rounds fire on whichever of size or interval comes first).
   */
  void onBatchCommitted() {
    final PersistTrigger trigger = sizeTrigger();
    if (trigger == null) {
      return;
    }
    if (roundInFlight() || mergeInFlight()) {
      sizeTriggerWhileInFlight = true;
      if (inFlightPacer != null) {
        // over budget while a round is already draining: stop pacing it — memory pressure beats
        // checkpoint spreading, so the drain finishes flat-out and the follow-up round starts
        // sooner
        inFlightPacer.expedite();
      }
      return;
    }
    if (domain.batchInFlight()) {
      return;
    }
    startRound(trigger);
  }

  /** The size trigger a round should be started for right now, or null if none applies. */
  private @Nullable PersistTrigger sizeTrigger() {
    if (domain.overCapacity()) {
      return PersistTrigger.OVER_CAPACITY;
    }
    if (maxBufferedBytes > 0 && domain.bufferedBytes() >= maxBufferedBytes) {
      return PersistTrigger.BUFFERED_BYTES;
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
   * @param flushed completed once the durable cut is fresh enough, or exceptionally if the
   *     pre-snapshot round fails — the snapshot must not proceed on a stale durable cut
   */
  void flushForSnapshot(final CompletableActorFuture<Void> flushed) {
    if (domain.batchInFlight()) {
      // a batch is mid-flight on the processor actor; run again once it completed
      processor.execute(() -> flushForSnapshot(flushed));
      return;
    }
    if (mergeInFlight()) {
      // the flush's round must not start while a merge is in flight; merges are quick
      inFlightMerge.onComplete((ignored, error) -> flushForSnapshot(flushed), processor);
      return;
    }
    if (roundInFlight()) {
      inFlightRound.onComplete((failure, ignored) -> flushForSnapshot(flushed), processor);
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
                flushed.completeExceptionally(
                    new ZeebeDbException(
                        "Failed to persist the buffered layered state before a snapshot; the"
                            + " snapshot must not proceed on a stale durable cut",
                        failure));
              }
            },
            processor);
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
    final DrainPacer pacer = new DrainPacer(pacingBudgetNanos, System.nanoTime());
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
                // after a failed round the next batch commit or tick retries anyway — only a
                // successful round needs the deferred size-trigger re-check
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
