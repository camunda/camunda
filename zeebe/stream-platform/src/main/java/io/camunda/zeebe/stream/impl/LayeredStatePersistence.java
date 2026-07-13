/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.ZeebeDbException;
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
 * Drives the persist and freeze cadences of a {@link LayeredZeebeDb}'s engine domain (experimental;
 * only active when the layered-state flag is on). Rounds are prepared and completed on the stream
 * processor's actor, but their drain-and-commit step runs on a dedicated IO executor (see {@link
 * PersistIo}) so processing continues while buffered state moves to RocksDB:
 *
 * <ul>
 *   <li>{@link #onPeriodicTick()} — the regular persist cadence, invoked at the configured persist
 *       interval; starts a round whenever anything is buffered and no round is in flight.
 *   <li>{@link #onFreezeTick()} — the freeze cadence, invoked at the configured freeze interval;
 *       freezes the active overlays into pipeline segments and republishes the read view whenever
 *       new batches were committed, bounding the staleness asynchronous view readers observe to
 *       roughly the freeze interval.
 *   <li>{@link #onBatchCommitted()} — invoked after every committed processing batch; starts a
 *       round as soon as the buffered bytes exceed their budget. While a round is in flight the
 *       trigger is only noted — rounds never stack — and re-checked once the round completed.
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
  private final Duration freezeInterval;

  /**
   * The completion of the single in-flight round, or null while none is; completes on the processor
   * actor — after the round was completed on the domain — with null on success or the round's
   * failure cause. Never completes exceptionally.
   */
  private CompletableActorFuture<Throwable> inFlightRound;

  /** An over-capacity trigger observed while a round was in flight; re-checked on completion. */
  private boolean overCapacityWhileInFlight;

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
    freezeInterval = db.config().freezeInterval();
    // build the coordinator eagerly: every layered column family must exist by now (they are all
    // created during recovery and record-processor init), and failing fast here beats failing on
    // the first persist round
    domain.coordinator();
  }

  Duration persistInterval() {
    return persistInterval;
  }

  Duration freezeInterval() {
    return freezeInterval;
  }

  /** Whether a persist round is currently in flight on the IO executor. */
  boolean roundInFlight() {
    return inFlightRound != null;
  }

  /** The regular persist cadence; a failed round stays buffered and the next tick retries. */
  void onPeriodicTick() {
    if (roundInFlight() || domain.batchInFlight() || !domain.hasBufferedWrites()) {
      return;
    }
    startRound(PersistTrigger.INTERVAL);
  }

  /**
   * The regular freeze cadence: freezes the active overlays into pipeline segments and republishes
   * the read view whenever batches were committed since the last freeze. Cheap — pointer swaps and
   * flattens, no durable IO, safe while a round is in flight (freezes only touch the newest,
   * non-persisting run of each pipeline). Skips while a batch is in flight (staging must be empty
   * on a freeze) and when there is nothing new to freeze; the next tick catches up.
   */
  void onFreezeTick() {
    if (domain.batchInFlight() || !domain.hasActiveWrites()) {
      return;
    }
    domain.freezeNow(lastProcessedPosition.getAsLong());
  }

  /**
   * Freezes the active overlays right before an asynchronous checker executes, so the read view the
   * checker acquires observes every batch committed before its execution — the freshness that keeps
   * event-driven checkers from losing wake-ups (a scan that misses a committed-but-unfrozen timer
   * could otherwise derive no next wake-up and never run again).
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
      domain.freezeNow(lastProcessedPosition.getAsLong());
    }
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
   * Notes an over-capacity condition right after a batch commit and starts a round for it once none
   * is in flight; triggers observed mid-round are re-checked on completion instead of stacking a
   * second round.
   */
  void onBatchCommitted() {
    if (!domain.overCapacity()) {
      return;
    }
    if (roundInFlight()) {
      overCapacityWhileInFlight = true;
      return;
    }
    if (domain.batchInFlight()) {
      return;
    }
    startRound(PersistTrigger.OVER_CAPACITY);
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
    final PersistRound round = domain.preparePersist(lastProcessedPosition.getAsLong(), trigger);
    final CompletableActorFuture<Throwable> completed = new CompletableActorFuture<>();
    inFlightRound = completed;
    io.persist(round)
        .onComplete(
            (ignored, error) -> {
              final boolean success = error == null;
              domain.completePersist(round, success);
              inFlightRound = null;
              if (!success) {
                // recoverable by design: the segments stay in their pipelines and the next round
                // retries them, while the log remains the durable source of everything buffered
                LOG.warn(
                    "Failed to persist buffered layered state (trigger: {}); will retry",
                    trigger,
                    error);
              }
              final boolean recheckCapacity = overCapacityWhileInFlight && success;
              overCapacityWhileInFlight = false;
              completed.complete(error);
              if (recheckCapacity) {
                // after a failed round the next batch commit or tick retries anyway — only a
                // successful round needs the deferred over-capacity re-check
                processor.execute(this::onBatchCommitted);
              }
            },
            processor);
    return completed;
  }

  /** Runs the persist step of a prepared round off the processor actor. */
  @FunctionalInterface
  interface PersistIo {

    /**
     * Runs {@link PersistRound#persist()} on an IO thread; the returned future completes
     * exceptionally when the drain failed or never ran.
     */
    ActorFuture<Void> persist(PersistRound round);
  }
}
