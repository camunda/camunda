/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.camunda.eventbridge.client.ConsumerNotRegisteredException;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.RecordExceptionHandler;
import io.camunda.eventbridge.streaming.Task;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorCondition;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processes one partition on its own actor. It <em>delegates</em> to a Zeebe {@link Actor} (built
 * via {@link Actor#newActor()}) rather than extending it, so the actor stays an implementation
 * detail: this class exposes only plain methods ({@link #offer}, {@link #signalWork}, {@link
 * #requestStop}) and is unit-testable without the actor's lifecycle leaking out.
 *
 * <p><b>Single-writer without locks.</b> An actor never runs two of its jobs at once, so folding,
 * punctuation, commit bookkeeping, and stop all run one-at-a-time on the actor thread — the
 * partition's {@link Task} and working state are touched by nothing else. This replaces the
 * lease/lock model with the framework's own guarantee.
 *
 * <p><b>Every commit is a cut.</b> At the barrier the actor freezes the cut ({@link Task#freezeCut}
 * — pointer swaps detaching the delta from the live state) and <b>keeps folding</b> while the sink
 * IO executor publishes and persists the frozen data it now exclusively owns ({@code cutInFlight}
 * gates only a second freeze; at most one cut is in flight). Folding pauses only in a <em>write
 * stall</em> (the RocksDB term): the task reports {@link Task#needsCheckpoint()} — its budget is
 * exhausted because both the active and the frozen entries are pinned — while the persist is still
 * running, the natural backpressure valve. The source offset advances only after the durable write
 * — the produce-before-commit cut is at the freeze barrier's offset — and completion (retire or
 * merge back) runs back on the actor thread. A cut completes only once both the transaction and the
 * chained source-offset ack are done, so single-flight covers the full cut. The final cut at
 * shutdown is the same contract executed inline on the actor thread (see {@link #finalizeStop()}).
 * Every task is a self-contained shard, so cuts of different partitions never contend on anything
 * durable.
 *
 * <p><b>Halt discipline (streaming ADR 0009 §4, interim contract).</b> Every other cut failure — a
 * failed publish/persist, or a source-offset commit rejected for any reason other than fencing —
 * merges the cut back for retry, as above. A source-offset commit rejected specifically because
 * this member was <em>fenced</em> (deposed by a successor that already owns the partition, surfaced
 * as {@link io.camunda.eventbridge.client.ConsumerNotRegisteredException} once the client's own
 * single rejoin-and-retry is exhausted) is different: retrying it would only repeat the same
 * rejection forever, since the cut's local transaction (and changelog append, if any) already
 * committed durably before that commit was even sent — so this halts the shard immediately instead,
 * in {@link #onCutPersisted}: no retry, no further cuts. See {@link #isHalted()}.
 *
 * @param <R> the decoded record type
 */
public final class PartitionActor<R> {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionActor.class);
  private static final Duration MIN_TIMER = Duration.ofMillis(1);

  private final Partition<R> partition;
  private final PartitionCommitter<R> committer;
  private final ExecutorService sinkExecutor;
  private final RecordExceptionHandler recordExceptionHandler;
  private final ToLongFunction<R> timestampExtractor;
  private final Duration punctuationInterval;
  private final long commitIntervalNanos;
  private final int maxProcessBatch;
  private final CutMetrics metrics;
  private final BooleanSupplier running;
  private final Runnable onFatal;

  private final Actor actor;
  private final CountDownLatch started = new CountDownLatch(1);
  private final CountDownLatch stopped = new CountDownLatch(1);

  // Touched only on the actor thread.
  private ActorControl control;
  private ActorCondition workAvailable;
  private ActorCondition stopSignal;
  // A frozen cut is being persisted on the IO thread. Folding continues; the flag only enforces
  // single-flight (no second freeze) and the budget-exhausted write stall.
  private boolean cutInFlight;
  // Whether the fold is currently write-stalled, so the stall counter counts entries into the
  // stall rather than every re-check; reset when the in-flight cut completes.
  private boolean writeStalled;
  private boolean stopRequested;
  private boolean finalized;
  // Set once a rejected (fenced) source-offset commit halts this shard (see the class javadoc's
  // "Halt discipline"): finalizeStop() then skips the final cut, since another attempt would only
  // repeat the same rejection.
  private volatile boolean halted;

  // The reused drain batch and the resume cursor into it. Entries drained from the queue live
  // ONLY here until handled — when a write stall (memory pressure while a cut is in flight) parks
  // the fold, the unprocessed tail stays in the batch and onWork resumes from batchNext, so no
  // drained entry is ever dropped (it is no longer in the queue; dropping it would silently lose
  // the record until a restart replays it). Actor thread only.
  private final List<SourceEntry<R>> batch = new ArrayList<>();
  private int batchNext;

  public PartitionActor(
      final Partition<R> partition,
      final PartitionCommitter<R> committer,
      final ExecutorService sinkExecutor,
      final RecordExceptionHandler recordExceptionHandler,
      final ToLongFunction<R> timestampExtractor,
      final Duration punctuationInterval,
      final long commitIntervalNanos,
      final int maxProcessBatch,
      final CutMetrics metrics,
      final BooleanSupplier running,
      final Runnable onFatal) {
    this.partition = partition;
    this.committer = committer;
    this.sinkExecutor = sinkExecutor;
    this.recordExceptionHandler = recordExceptionHandler;
    this.timestampExtractor = timestampExtractor;
    this.punctuationInterval = punctuationInterval;
    this.commitIntervalNanos = commitIntervalNanos;
    this.maxProcessBatch = maxProcessBatch;
    this.metrics = metrics;
    this.running = running;
    this.onFatal = onFatal;
    actor =
        Actor.newActor()
            .name("eb-partition-" + partition.id())
            .actorStartedHandler(this::onStarted)
            .build();
  }

  /** The delegated actor, for the runtime to submit to its {@code ActorScheduler}. */
  public Actor actor() {
    return actor;
  }

  /**
   * Blocks until the actor's start handler has registered its conditions and timers, so a caller
   * may safely {@link #offer}/{@link #signalWork}/{@link #requestStop} afterwards. Submitting the
   * actor to the scheduler does not itself guarantee the start handler has run.
   */
  public void awaitStarted() throws InterruptedException {
    started.await();
  }

  public int id() {
    return partition.id();
  }

  /**
   * Whether this shard halted after a rejected (fenced) source-offset commit (see the class
   * javadoc's "Halt discipline"). Once {@code true} it never reverts: no further cuts run for this
   * partition. Safe to read from any thread.
   */
  public boolean isHalted() {
    return halted;
  }

  /**
   * The dedup baseline; immutable after materialization, so safe to read from the source thread.
   */
  public long baseline() {
    return partition.baseline();
  }

  /**
   * Enqueues a decoded entry, blocking the caller (the source thread) when full — back-pressure.
   */
  public void offer(final SourceEntry<R> entry) throws InterruptedException {
    partition.queue().put(entry);
  }

  /**
   * Enqueues a decoded entry without blocking, returning {@code false} when the partition's queue
   * is full — the caller (the source thread) pauses the partition instead of blocking on it.
   */
  public boolean tryOffer(final SourceEntry<R> entry) {
    return partition.queue().offer(entry);
  }

  /**
   * The free capacity of the partition's queue. The queue is SPSC with the source thread as its
   * only producer, so a value read from the source thread is conservatively safe: the consumer only
   * drains concurrently, meaning the true free capacity is at least the returned value.
   */
  public int queueRemainingCapacity() {
    return partition.queue().remainingCapacity();
  }

  /** The fixed capacity of the partition's queue (power-of-two rounded by the backing queue). */
  public int queueCapacity() {
    return partition.queue().capacity();
  }

  /** Wakes the actor to drain its queue; safe to call from the source thread. */
  public void signalWork() {
    workAvailable.signal();
  }

  /** Requests a graceful stop (final cut + close); safe to call from the source thread. */
  public void requestStop() {
    stopSignal.signal();
  }

  /** Waits for the actor to finish its final cut and close its task. */
  public boolean awaitStopped(final long timeoutMs) throws InterruptedException {
    return stopped.await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  private void onStarted(final ActorControl control) {
    this.control = control;
    workAvailable = control.onCondition("work-" + partition.id(), this::onWork);
    stopSignal = control.onCondition("stop-" + partition.id(), this::onStop);
    final Duration tick =
        punctuationInterval.compareTo(MIN_TIMER) < 0 ? MIN_TIMER : punctuationInterval;
    control.runAtFixedRate(tick, this::onPunctuationTick);
    started.countDown();
  }

  /**
   * Folds the current batch (resuming a tail a write stall left behind), refilling it from the
   * queue when exhausted, then commits if due. Runs on the actor thread.
   */
  private void onWork() {
    if (finalized || !running.getAsBoolean()) {
      return;
    }
    if (batchNext >= batch.size()) {
      batch.clear();
      batchNext = 0;
      partition.queue().drainTo(batch, maxProcessBatch);
    }
    while (batchNext < batch.size()) {
      final SourceEntry<R> entry = batch.get(batchNext);
      batchNext++;
      handleEntry(entry);
      if (finalized || !running.getAsBoolean()) {
        return; // a fatal error stopped the runtime — the unprocessed tail stays in the batch
        // and resumes from batchNext
      }
      if (partition.task().needsCheckpoint()) {
        if (cutInFlight) {
          // Write stall: budget exhausted (active + frozen entries are pinned) while a cut is
          // still persisting — the batch tail stays and onCutPersisted resumes it. Count only
          // the entry into the stall, not every re-check while stalled.
          if (!writeStalled) {
            writeStalled = true;
            metrics.countWriteStall();
          }
          return;
        }
        metrics.countEarlyCut();
        beginCommit();
        // The cut is in flight but detached: keep folding — the write stall above kicks in only
        // if the budget is still exhausted while the persist runs.
      }
    }
    maybeCommit();
    if (!partition.queue().isEmpty()) {
      control.submit(this::onWork); // keep draining, yielding so timers still interleave
    }
  }

  private void handleEntry(final SourceEntry<R> entry) {
    final long offset = entry.offset();
    if (offset <= partition.baseline()) {
      return; // already folded into durable state (resume-gap dedup)
    }
    if (entry instanceof SourceEntry.Filtered<R> filtered) {
      // A coalesced filtered run: nothing to fold, but the commit position advances past it so
      // commits and watermark seals keep moving during filtered-only stretches — and so does
      // stream time, when the source could peek the run's event time (MIN_VALUE is inert).
      partition.markProcessed(offset);
      partition.observeStreamTime(filtered.eventTimeMs());
      return;
    }
    if (entry instanceof SourceEntry.DecodeFailure<R> failure) {
      onRecordError(offset, failure.cause());
      return;
    }
    final R record = ((SourceEntry.Decoded<R>) entry).record();
    try {
      partition.task().process(record);
    } catch (final RuntimeException e) {
      onRecordError(offset, e);
      return;
    }
    partition.markProcessed(offset);
    if (timestampExtractor != null) {
      partition.observeStreamTime(timestampExtractor.applyAsLong(record));
    }
  }

  private void onRecordError(final long offset, final RuntimeException cause) {
    if (recordExceptionHandler.onError(partition.id(), offset, cause)
        == RecordExceptionHandler.Decision.SKIP) {
      LOG.warn("Skipping record {}-{} after error", partition.id(), offset, cause);
      partition.markProcessed(offset); // advance past it so it commits, not retries
      return;
    }
    LOG.error(
        "Fatal error on record {}-{}; stopping runtime (restart resumes from last commit)",
        partition.id(),
        offset,
        cause);
    onFatal.run();
  }

  /** The freshness tick and the idle-commit driver. Runs on the actor thread. */
  private void onPunctuationTick() {
    if (finalized || !running.getAsBoolean()) {
      return;
    }
    final Task<R> task = partition.task();
    task.flush();
    task.punctuateWallClock(System.currentTimeMillis());
    // Stream time is fed by the timestamp extractor and by filtered-run peeks; hasStreamTime()
    // stays false until either observed something, so no extra null guard is needed.
    if (partition.hasStreamTime()) {
      task.advanceStreamTime(partition.streamTime());
    }
    maybeCommit();
  }

  private void maybeCommit() {
    if (cutInFlight || finalized || !partition.hasPending()) {
      return;
    }
    if (System.nanoTime() - partition.lastCommitNanos() >= commitIntervalNanos) {
      beginCommit();
    }
  }

  /**
   * Starts a commit: freeze a cut at the current barrier and keep folding while the IO thread
   * persists it. Actor thread only.
   */
  private void beginCommit() {
    if (cutInFlight || !partition.hasPending()) {
      return;
    }
    final long offset = partition.pending();
    // The barrier: the freeze converges buffered output and detaches the cut — cheap actor-thread
    // work; the durable write is what gets offloaded. The freeze timer is the residual pause.
    final long freezeStart = System.nanoTime();
    final CommitCut cut = partition.task().freezeCut(offset);
    metrics.observeFreeze(System.nanoTime() - freezeStart);
    cutInFlight = true;
    final CompletableActorFuture<Void> persisted = new CompletableActorFuture<>();
    sinkExecutor.execute(
        () -> {
          // IO-thread pickup to full cut completion (transaction plus source-offset ack): the
          // pause a synchronous barrier would impose on the fold — the feature's measured win.
          // The timer deliberately keeps spanning the offset ack so runs stay comparable, even
          // though the IO thread's *occupancy* ends at the transaction commit; the ack completes
          // on the client's network thread.
          final long persistStart = System.nanoTime();
          try {
            committer
                .persistCut(partition, offset, cut)
                .whenComplete(
                    (ignored, error) -> {
                      // Possibly on the client's network thread; runOnCompletion below marshals
                      // the completion back onto the actor thread.
                      if (error != null) {
                        persisted.completeExceptionally(error);
                      } else {
                        metrics.observePersist(System.nanoTime() - persistStart);
                        persisted.complete(null);
                      }
                    });
          } catch (final Throwable t) {
            persisted.completeExceptionally(t);
          }
        });
    control.runOnCompletion(persisted, (ignored, error) -> onCutPersisted(offset, cut, error));
  }

  /**
   * A frozen cut finished persisting: retire it (or merge it back for retry), update the commit
   * bookkeeping, and resume anything the write stall parked. Actor thread only.
   */
  private void onCutPersisted(final long offset, final CommitCut cut, final Throwable error) {
    cutInFlight = false;
    writeStalled = false;
    if (error != null && isFencedOffsetCommitRejection(error)) {
      // Halt discipline (streaming ADR 0009 §4, interim contract ahead of broker-enforced epoch
      // fencing): the source-offset commit was rejected because this member was fenced — deposed
      // by a successor that already owns the partition. The cut's changelog append (if any) and
      // local transaction ran strictly BEFORE this commit in the chain (PartitionCommitter), so
      // nothing durable was lost; only the advisory offset bookkeeping was rejected. Retrying
      // would only repeat the same rejection forever, so this halts the shard instead of merging
      // back: no retry, no further cuts. The successor re-folds the same source records
      // deterministically and re-persists the same keys, so the blast radius is at most one
      // harmless "zombie cut" of duplicated (idempotent) output — the bound the ADR accepts.
      cut.complete(true);
      halted = true;
      metrics.countHalt();
      LOG.error(
          "Partition {} halted at offset {}: the source-offset commit was rejected (fenced); no"
              + " further cuts will run for this shard",
          id(),
          offset,
          error);
      finalizeStop();
      return;
    }
    cut.complete(error == null);
    if (error != null) {
      // Merged back: the next freeze re-includes this cut's delta. The commit clock was not
      // reset, so maybeCommit retries on the next tick. A failed source-offset commit funnels
      // into this same path even though its transaction already committed — re-persisting that
      // delta as part of the next cut is idempotent (the same values land under the same keys),
      // and the retry re-sends the advisory offset advance.
      metrics.countRetry();
      LOG.warn(
          "Persisting the cut of partition {} at offset {} failed; will retry",
          id(),
          offset,
          error);
    } else {
      if (partition.pending() == offset) {
        partition.clearPending(); // nothing was folded past the barrier while persisting
      }
      partition.markCommitted(System.nanoTime());
    }
    if (stopRequested) {
      finalizeStop();
      return;
    }
    // Resume a write-stalled batch tail (if any), then anything queued during the persist.
    control.submit(this::onWork);
  }

  /**
   * Whether {@code error} is (or wraps, at any depth — {@code CompletableFuture} composition may
   * nest a {@code CompletionException}) a {@link ConsumerNotRegisteredException} — the coordinator
   * rejecting a source-offset commit because this member's epoch is stale (fenced). This is the
   * only failure this actor treats as a halt rather than a retry; every other cut failure
   * (including any other {@code commitOffset} rejection) merges back for retry as before.
   */
  private static boolean isFencedOffsetCommitRejection(final Throwable error) {
    Throwable cause = error;
    while (cause != null) {
      if (cause instanceof ConsumerNotRegisteredException) {
        return true;
      }
      final Throwable next = cause.getCause();
      cause = next == cause ? null : next;
    }
    return false;
  }

  private void onStop() {
    stopRequested = true;
    finalizeStop();
  }

  /**
   * Final cut + close, once no cut is in flight. Actor thread only. A halted shard (see the class
   * javadoc's "Halt discipline") skips the final cut entirely — another attempt would only repeat
   * the same fencing rejection — and only closes the task.
   */
  private void finalizeStop() {
    if (finalized || cutInFlight) {
      return; // an in-flight cut finishes first; its completion re-enters finalizeStop
    }
    finalized = true;
    if (!halted && partition.hasPending()) {
      finalCut(partition.pending());
    }
    partition.task().close();
    stopped.countDown();
  }

  /**
   * The final cut of the shard: the same contract as every other commit — freeze → publish →
   * persist → complete — executed inline on the actor thread, with the source-offset commit joined
   * (shutdown legitimately waits for durability). Inline execution trivially satisfies the cut's
   * threading contract: the freezing thread and the persisting thread are the same thread, and no
   * folding is concurrent because this actor runs nothing else while finalizing. Actor thread only.
   */
  private void finalCut(final long offset) {
    // The freeze and persist record the usual timers, so the stop cut appears in the same metric
    // stream as every in-flight cut; a failed final persist is NOT counted as a retry, because
    // nothing retries it.
    final long freezeStart = System.nanoTime();
    final CommitCut cut = partition.task().freezeCut(offset);
    metrics.observeFreeze(System.nanoTime() - freezeStart);
    try {
      final long persistStart = System.nanoTime();
      committer.persistCut(partition, offset, cut).join();
      metrics.observePersist(System.nanoTime() - persistStart);
      cut.complete(true);
      partition.clearPending();
    } catch (final RuntimeException e) {
      // Merge the cut back and proceed with the close anyway: the delta was not lost, only not
      // persisted — after a restart, replay from the last durable cut re-folds the remainder.
      cut.complete(false);
      LOG.warn(
          "Final cut of partition {} at offset {} failed; closing anyway — replay from the last"
              + " durable cut covers the remainder",
          id(),
          offset,
          e);
    }
  }
}
