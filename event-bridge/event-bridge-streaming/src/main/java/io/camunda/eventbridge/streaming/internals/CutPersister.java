/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.streaming.CommitCut;
import io.camunda.eventbridge.streaming.OffsetStore;
import io.camunda.eventbridge.streaming.TransactionRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The dedicated writer for the runtime-managed durability path: partitions whose tasks defer
 * durability share one transaction runner, offset store and set of pre-commit publishers, and
 * <em>everything</em> that writes those shared resources — frozen cuts, legacy suspended commits
 * and final stop commits — runs on this single daemon thread. That is the invariant this class
 * exists for: <b>the persister thread is the only thread that ever touches the shared durable
 * resources; exclusion is structural, no lock exists.</b> Tasks that own their durability never
 * come through here; their cuts and commits run directly (sink executor / actor thread), fully
 * parallel — they share nothing.
 *
 * <p><b>Two kinds of work.</b> A <em>frozen cut</em> ({@link #enqueue}) carries data already
 * detached from the live task, so its actor keeps folding while it waits here. An <em>exclusive
 * job</em> ({@link #submit}) is a legacy suspended commit or a final stop commit: it runs the full
 * synchronous commit sequence — including reads and writes of the <em>live</em> task — on this
 * thread, which is safe because the submitting actor stays suspended ({@code committing} /
 * finalizing) for the whole commit, so exactly one thread touches that task at a time.
 *
 * <p><b>Coalescing.</b> When the writer wakes and finds several cuts queued, it persists them as
 * one batch: the pre-commit publishes run once, each cut's {@link CommitCut#publish()} runs in
 * enqueue order, then <em>one</em> transaction carries every cut's offset write and {@link
 * CommitCut#persist()}. Each cut still completes individually — its future settles when its own
 * source-offset ack arrives. An exclusive job is <em>never</em> coalesced into a frozen batch's
 * transaction — it brings its own — so it segments the drained work: everything executes in enqueue
 * order, with consecutive cuts batching and each job running alone between batches.
 *
 * <p><b>One transaction, one fate.</b> Any failure while making a batch durable — a publish or the
 * shared transaction — fails <em>every</em> cut in the batch: each merges back on its actor and
 * retries individually at its next barrier. Splitting the batch around a failed publish is not
 * needed for correctness, because publishes are idempotent by the {@link CommitCut#publish()}
 * contract and nothing of the batch is committed until the single transaction; a cut whose output
 * already landed simply re-publishes it when it retries. This is the simplest rule that cannot
 * poison an already-published cut.
 *
 * <p><b>Per-partition order.</b> The partition actor's single-flight rule (at most one cut in
 * flight per partition) guarantees at most one cut per partition is ever queued here, so a batch
 * never contains two cuts of the same partition and no ordered application within a batch is
 * needed. {@link #enqueue} verifies the protocol instead of trusting it.
 *
 * <p><b>Lifecycle.</b> The writer thread starts lazily on the first enqueue/submit — a runtime
 * whose tasks all own their durability never starts it — and {@link #close()} drains anything still
 * queued before stopping it. {@link io.camunda.eventbridge.streaming.StreamRuntime}'s shutdown
 * closes the persister only <em>after</em> every partition actor has stopped, so a stop commit
 * submitted from an actor always finds it open. Should a straggler actor still submit after {@link
 * #close()} (it missed the shutdown wait), {@link #submit} does not hang or fail: it waits for the
 * writer thread to end and then runs the job inline on the caller thread — trivially exclusive,
 * because the persister thread no longer exists. (The lifecycle fields below are guarded by the
 * instance monitor; that monitor never wraps a durable write.)
 */
final class CutPersister {

  private static final Logger LOG = LoggerFactory.getLogger(CutPersister.class);
  private static final long CLOSE_TIMEOUT_MS = 30_000;

  /** Wakes the writer for shutdown; work queued before it is still drained and executed. */
  private static final Queued STOP = new StopMarker();

  private final Consumer consumer;
  private final String sourceTopic;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;

  private final BlockingQueue<Queued> queued = new LinkedBlockingQueue<>();
  private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

  // Guarded by `this`; the queue itself is thread-safe.
  private Thread writer;
  private boolean closed;

  CutPersister(
      final Consumer consumer,
      final String sourceTopic,
      final TransactionRunner transactionRunner,
      final OffsetStore offsets,
      final List<Runnable> preCommitFlushes) {
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
    this.transactionRunner = transactionRunner;
    this.offsets = offsets;
    this.preCommitFlushes = preCommitFlushes;
  }

  /**
   * Hands a frozen cut of the runtime-managed durability path to the writer and returns the future
   * the caller chains the cut's completion onto; it settles once the cut's transaction (possibly
   * shared with coalesced peers) committed and its source-offset ack arrived, or exceptionally on
   * the first failure along that path.
   */
  CompletableFuture<Void> enqueue(
      final int partitionId, final long offset, final CommitCut cut, final CutMetrics metrics) {
    if (!inFlight.add(partitionId)) {
      // Single-flight per partition is the actor's invariant; a second cut of the same partition
      // here means the commit protocol broke, so fail loudly instead of persisting out of order.
      return CompletableFuture.failedFuture(
          new IllegalStateException(
              "a cut of partition %d is already pending — single-flight violated"
                  .formatted(partitionId)));
    }
    final PendingCut pending =
        new PendingCut(partitionId, offset, cut, metrics, new CompletableFuture<>());
    synchronized (this) {
      if (closed) {
        inFlight.remove(partitionId);
        return CompletableFuture.failedFuture(
            new IllegalStateException("the cut persister is closed"));
      }
      startWriter();
      // Inside the monitor so a concurrent close() cannot slip its stop marker in between the
      // closed check and this add — every accepted cut is queued ahead of the marker and drained.
      queued.add(pending);
    }
    return pending.completed();
  }

  /**
   * Hands a legacy suspended commit or a final stop commit of a runtime-managed task to the writer
   * and returns the future that settles when the job ran (exceptionally with whatever it threw).
   * The job executes on the persister thread, in enqueue order with the frozen cuts, in its own
   * right — never inside a frozen batch's transaction.
   *
   * <p>After {@link #close()} the job is not lost: once the writer thread has ended, it runs inline
   * on the calling thread, which at that point is trivially the only writer of the shared durable
   * resources. This is the escape hatch for an actor whose stop outlived the runtime's shutdown
   * wait; the normal shutdown ordering (persister closed only after all actors stopped) never takes
   * it.
   */
  CompletableFuture<Void> submit(final Runnable job) {
    final ExclusiveJob exclusive = new ExclusiveJob(job, new CompletableFuture<>());
    final Thread endedWriter;
    synchronized (this) {
      if (!closed) {
        startWriter();
        queued.add(exclusive);
        return exclusive.completed();
      }
      endedWriter = writer;
    }
    // Late submit after close(): the writer was already told to stop (or never started). Wait for
    // it to end, then run inline — single-writer holds because the persister thread is gone.
    if (endedWriter != null) {
      try {
        endedWriter.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        exclusive
            .completed()
            .completeExceptionally(
                new IllegalStateException(
                    "interrupted while waiting for the closed cut persister of topic '%s' to end"
                        .formatted(sourceTopic),
                    e));
        return exclusive.completed();
      }
    }
    runExclusive(exclusive);
    return exclusive.completed();
  }

  /** Drains any still-queued work, then stops the writer thread. Idempotent. */
  void close() {
    final Thread thread;
    synchronized (this) {
      if (closed) {
        return;
      }
      closed = true;
      thread = writer;
    }
    if (thread == null) {
      return; // never started — nothing was ever queued
    }
    queued.add(STOP);
    try {
      thread.join(CLOSE_TIMEOUT_MS);
      if (thread.isAlive()) {
        LOG.warn(
            "Cut persister of topic '{}' did not drain within {}ms", sourceTopic, CLOSE_TIMEOUT_MS);
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void startWriter() {
    if (writer == null) {
      writer = new Thread(this::run, "eb-cut-persister-" + sourceTopic);
      writer.setDaemon(true);
      writer.start();
    }
  }

  private void run() {
    final List<Queued> drained = new ArrayList<>();
    final List<PendingCut> batch = new ArrayList<>();
    while (true) {
      drained.clear();
      try {
        drained.add(queued.take());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      queued.drainTo(drained);
      final boolean stop = drained.removeIf(item -> item == STOP);
      // Execute in enqueue order: consecutive frozen cuts coalesce into one batch, an exclusive
      // job segments them and runs alone with its own transaction (see class javadoc).
      int next = 0;
      while (next < drained.size()) {
        if (drained.get(next) instanceof ExclusiveJob job) {
          runExclusive(job);
          next++;
          continue;
        }
        batch.clear();
        while (next < drained.size() && drained.get(next) instanceof PendingCut cut) {
          batch.add(cut);
          next++;
        }
        persistBatch(batch);
      }
      if (stop) {
        return;
      }
    }
  }

  private void runExclusive(final ExclusiveJob job) {
    try {
      job.work().run();
      job.completed().complete(null);
    } catch (final Throwable failure) {
      job.completed().completeExceptionally(failure);
    }
  }

  private void persistBatch(final List<PendingCut> batch) {
    try {
      preCommitFlushes.forEach(Runnable::run);
      for (final PendingCut pending : batch) {
        pending.cut().publish();
      }
      transactionRunner.runInTransaction(
          () -> {
            for (final PendingCut pending : batch) {
              offsets.store(pending.partitionId(), pending.offset());
              pending.cut().persist();
            }
          });
    } catch (final Throwable failure) {
      // One transaction, one fate (see class javadoc): every cut of the batch merges back on its
      // actor and retries individually at its next barrier.
      batch.forEach(pending -> fail(pending, failure));
      return;
    }
    if (batch.size() > 1) {
      batch.forEach(pending -> pending.metrics().countCoalesced());
    }
    // Chain each cut's advisory source-offset commit without parking this thread — the
    // authoritative resume bookmark is already durable inside the transaction. Each cut completes
    // individually when its ack arrives (possibly on the client's network thread).
    for (final PendingCut pending : batch) {
      chainOffsetCommit(pending);
    }
  }

  private void chainOffsetCommit(final PendingCut pending) {
    final CompletableFuture<Void> ack;
    try {
      ack = consumer.commitOffset(sourceTopic, pending.partitionId(), pending.offset());
    } catch (final RuntimeException e) {
      fail(pending, e);
      return;
    }
    ack.whenComplete(
        (ignored, error) -> {
          if (error != null) {
            fail(pending, error);
          } else {
            inFlight.remove(pending.partitionId());
            pending.completed().complete(null);
          }
        });
  }

  private void fail(final PendingCut pending, final Throwable failure) {
    inFlight.remove(pending.partitionId());
    pending.completed().completeExceptionally(failure);
  }

  /** Everything the writer's queue can carry, so drains stay type-safe without casts. */
  private sealed interface Queued permits PendingCut, ExclusiveJob, StopMarker {}

  /** One queued cut: the frozen data plus where it belongs and whom to tell when it is durable. */
  private record PendingCut(
      int partitionId,
      long offset,
      CommitCut cut,
      CutMetrics metrics,
      CompletableFuture<Void> completed)
      implements Queued {}

  /** A legacy suspended commit or final stop commit, run whole on the persister thread. */
  private record ExclusiveJob(Runnable work, CompletableFuture<Void> completed) implements Queued {}

  /** The shutdown wake-up marker; identity-compared, carries nothing. */
  private static final class StopMarker implements Queued {}
}
