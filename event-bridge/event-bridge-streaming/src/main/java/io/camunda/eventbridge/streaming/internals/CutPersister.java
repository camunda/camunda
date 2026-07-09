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
 * durability share one transaction runner, offset store and set of pre-commit publishers, so their
 * frozen cuts are made durable by this single daemon thread instead of serializing several
 * IO-executor threads under a monitor — a blocked commit no longer pins an IO thread doing nothing.
 * Tasks that own their durability never come through here; their cuts run directly on the sink
 * executor, fully parallel.
 *
 * <p><b>Coalescing.</b> When the writer wakes and finds several cuts queued, it persists them as
 * one batch: the pre-commit publishes run once, each cut's {@link CommitCut#publish()} runs in
 * enqueue order, then <em>one</em> transaction carries every cut's offset write and {@link
 * CommitCut#persist()}. Each cut still completes individually — its future settles when its own
 * source-offset ack arrives.
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
 * <p><b>Lifecycle.</b> The writer thread starts lazily on the first enqueue — a runtime whose tasks
 * all own their durability never starts it — and {@link #close()} drains anything still queued
 * before stopping it. The final synchronous stop commit of a partition does not come through here
 * (it stays on {@link PartitionCommitter#commit}), which is why the shared monitor still exists and
 * this writer takes it around each batch.
 */
final class CutPersister {

  private static final Logger LOG = LoggerFactory.getLogger(CutPersister.class);
  private static final long CLOSE_TIMEOUT_MS = 30_000;

  /** Wakes the writer for shutdown; drained cuts queued before it are still persisted. */
  private static final PendingCut STOP = new PendingCut(-1, -1L, null, null, null);

  private final Consumer consumer;
  private final String sourceTopic;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;
  private final Object sharedDurability;

  private final BlockingQueue<PendingCut> queued = new LinkedBlockingQueue<>();
  private final Set<Integer> inFlight = ConcurrentHashMap.newKeySet();

  // Guarded by `this`; the queue itself is thread-safe.
  private Thread writer;
  private boolean closed;

  CutPersister(
      final Consumer consumer,
      final String sourceTopic,
      final TransactionRunner transactionRunner,
      final OffsetStore offsets,
      final List<Runnable> preCommitFlushes,
      final Object sharedDurability) {
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
    this.transactionRunner = transactionRunner;
    this.offsets = offsets;
    this.preCommitFlushes = preCommitFlushes;
    this.sharedDurability = sharedDurability;
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
      if (writer == null) {
        writer = new Thread(this::run, "eb-cut-persister-" + sourceTopic);
        writer.setDaemon(true);
        writer.start();
      }
      // Inside the monitor so a concurrent close() cannot slip its stop marker in between the
      // closed check and this add — every accepted cut is queued ahead of the marker and drained.
      queued.add(pending);
    }
    return pending.completed();
  }

  /** Drains any still-queued cuts, then stops the writer thread. Idempotent. */
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

  private void run() {
    final List<PendingCut> batch = new ArrayList<>();
    while (true) {
      batch.clear();
      try {
        batch.add(queued.take());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      queued.drainTo(batch);
      final boolean stop = batch.removeIf(pending -> pending == STOP);
      if (!batch.isEmpty()) {
        persistBatch(batch);
      }
      if (stop) {
        return;
      }
    }
  }

  private void persistBatch(final List<PendingCut> batch) {
    try {
      // The final synchronous stop commit still writes the shared resources directly
      // (PartitionCommitter#commit), so the batch takes the same monitor — uncontended except
      // around shutdown.
      synchronized (sharedDurability) {
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
      }
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

  /** One queued cut: the frozen data plus where it belongs and whom to tell when it is durable. */
  private record PendingCut(
      int partitionId,
      long offset,
      CommitCut cut,
      CutMetrics metrics,
      CompletableFuture<Void> completed) {}
}
