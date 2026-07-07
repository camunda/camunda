/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

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
 * <p><b>Async commit.</b> The commit is the only step that blocks on a DB sink, so it is the only
 * thing offloaded: the blocking {@link PartitionCommitter#commit} runs on an IO executor while the
 * actor is <em>suspended</em> ({@code committing} is set, so no folding/punctuation/second commit
 * runs), giving the IO thread exclusive access to the task. The source offset advances only inside
 * that commit, after the sink write is durable — produce-before-commit intact, now off the
 * processing thread. When it completes, {@link #onCommitted} resumes folding on the actor thread.
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
  private final BooleanSupplier running;
  private final Runnable onFatal;

  private final Actor actor;
  private final CountDownLatch started = new CountDownLatch(1);
  private final CountDownLatch stopped = new CountDownLatch(1);

  // Touched only on the actor thread.
  private ActorControl control;
  private ActorCondition workAvailable;
  private ActorCondition stopSignal;
  private boolean committing;
  private boolean stopRequested;
  private boolean finalized;

  // The reused drain batch and the resume cursor into it. Entries drained from the queue live
  // ONLY here until handled — when a mid-batch commit (memory pressure) suspends the fold, the
  // unprocessed tail stays in the batch and onWork resumes from batchNext, so no drained entry is
  // ever dropped (it is no longer in the queue; dropping it would silently lose the record until
  // a restart replays it). Actor thread only.
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

  /** Wakes the actor to drain its queue; safe to call from the source thread. */
  public void signalWork() {
    workAvailable.signal();
  }

  /** Requests a graceful stop (final commit + close); safe to call from the source thread. */
  public void requestStop() {
    stopSignal.signal();
  }

  /** Waits for the actor to finish its final commit and close its task. */
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
   * Folds the current batch (resuming a tail a mid-batch commit left behind), refilling it from the
   * queue when exhausted, then commits if due. Runs on the actor thread.
   */
  private void onWork() {
    if (committing || finalized || !running.getAsBoolean()) {
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
      if (committing || finalized || !running.getAsBoolean()) {
        return; // a fatal error stopped the runtime, or a commit suspended the fold — the
        // unprocessed tail stays in the batch and resumes from batchNext
      }
      if (partition.task().needsCheckpoint()) {
        beginCommit();
        return; // resumes via onCommitted, continuing this batch from batchNext
      }
    }
    maybeCommit();
    if (!committing && !partition.queue().isEmpty()) {
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
    if (committing || finalized || !running.getAsBoolean()) {
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
    if (committing || finalized || !partition.hasPending()) {
      return;
    }
    if (System.nanoTime() - partition.lastCommitNanos() >= commitIntervalNanos) {
      beginCommit();
    }
  }

  /** Offloads the blocking commit to the IO executor; resumes on completion. Actor thread only. */
  private void beginCommit() {
    if (committing || !partition.hasPending()) {
      return;
    }
    committing = true;
    final long offset = partition.pending();
    final CompletableActorFuture<Void> committed = new CompletableActorFuture<>();
    sinkExecutor.execute(
        () -> {
          try {
            committer.commit(partition, offset);
            committed.complete(null);
          } catch (final Throwable t) {
            committed.completeExceptionally(t);
          }
        });
    control.runOnCompletion(committed, (ignored, error) -> onCommitted(offset, error));
  }

  private void onCommitted(final long offset, final Throwable error) {
    committing = false;
    if (error != null) {
      LOG.warn("Commit of partition {} at offset {} failed; will retry", id(), offset, error);
    } else {
      // No folding ran while committing, so pending is still this offset.
      partition.clearPending();
      partition.markCommitted(System.nanoTime());
    }
    if (stopRequested) {
      finalizeStop();
      return;
    }
    // Resume the suspended batch tail first (if any), then anything queued during the commit.
    control.submit(this::onWork);
  }

  private void onStop() {
    stopRequested = true;
    finalizeStop();
  }

  /** Final synchronous commit + close, once no commit is in flight. Actor thread only. */
  private void finalizeStop() {
    if (finalized || committing) {
      return; // an in-flight commit finishes first; onCommitted re-enters finalizeStop
    }
    finalized = true;
    if (partition.hasPending()) {
      try {
        committer.commit(partition, partition.pending());
        partition.clearPending();
      } catch (final RuntimeException e) {
        LOG.warn("Final commit of partition {} failed", id(), e);
      }
    }
    partition.task().close();
    stopped.countDown();
  }
}
