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
import io.camunda.eventbridge.streaming.Task;
import io.camunda.eventbridge.streaming.TransactionRunner;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Makes one partition an independent atomic cut: emit its output, make it durable, persist state
 * and offset together, then advance that partition's source offset. Invoked by the partition's
 * leaseholder, so it is single-writer over that partition's task and state.
 *
 * <p><b>Two durability modes.</b> A task that {@linkplain Task#ownsDurability() owns its
 * durability} (its own state backend, offset store and output sink) makes its own cut via {@link
 * Task#commit} and shares nothing — so partitions commit fully in parallel, directly on the calling
 * thread ({@link #commit}). A task that defers to the runtime-managed durability shares the {@link
 * TransactionRunner}, {@link OffsetStore}, and the pre-commit flushes; <em>every</em> write of
 * those shared resources — frozen cuts ({@link #persistCut}), legacy suspended commits and final
 * stop commits ({@link #commitSuspended}) — is executed by the {@link CutPersister}'s single writer
 * thread, so exclusion is structural: no lock guards the shared durable path. Advancing the source
 * offset uses the thread-safe consumer and needs no coordination either.
 *
 * @param <R> the decoded record type
 */
public final class PartitionCommitter<R> {

  private final Consumer consumer;
  private final String sourceTopic;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;
  private final CutPersister cutPersister;

  public PartitionCommitter(
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
    cutPersister =
        new CutPersister(consumer, sourceTopic, transactionRunner, offsets, preCommitFlushes);
  }

  /**
   * Makes a frozen cut durable: publish its produced output, persist its state delta and {@code
   * offset}, then send the source-offset advance and return a future <em>without joining on
   * it</em>. Runs on an IO thread while the partition's actor <em>keeps folding</em> — the cut is
   * detached from the live working state at the freeze barrier, so no suspension is needed. The
   * completion (retire or merge back) happens afterwards on the actor thread, chained onto the
   * returned future, not here.
   *
   * @return a future done only when the cut is fully complete — its transaction committed and its
   *     source-offset ack arrived
   */
  public CompletableFuture<Void> persistCut(
      final Partition<R> partition,
      final long offset,
      final CommitCut cut,
      final CutMetrics metrics) {
    if (partition.task().ownsDurability()) {
      // Self-contained shard: the cut publishes and persists through the task's own sinks and
      // transaction on this IO thread, fully in parallel with other partitions' cuts.
      cut.publish();
      cut.persist();
      // The source-offset commit is advisory — the authoritative resume bookmark was just
      // persisted inside the transaction — so it is sent after the transaction but not awaited:
      // the IO thread's occupancy ends here, and the caller chains the cut's completion onto the
      // returned future. The one hard ordering rule holds by construction: the commit is never
      // sent before the transaction committed.
      return consumer.commitOffset(sourceTopic, partition.id(), offset);
    }
    // Runtime-managed durability: hand the cut to the single persister thread, which coalesces
    // queued cuts into one shared transaction (offset and frozen state land atomically at the
    // barrier's offset) and chains each cut's source-offset commit the same non-joining way.
    return cutPersister.enqueue(partition.id(), offset, cut, metrics);
  }

  /**
   * Commits a runtime-managed partition whose actor is <em>suspended</em> for the whole commit — a
   * legacy commit of a task without frozen-cut support, or the final stop commit — by enqueuing the
   * full synchronous sequence ({@link #commit}) as an exclusive job on the {@link CutPersister}'s
   * writer thread, keeping that thread the only writer of the shared durable resources. Touching
   * the <em>live</em> task there is safe precisely because the actor is suspended until the
   * returned future settles: any single thread may access the task then, and the persister thread
   * is that thread.
   *
   * <p>Must not be called for a task that {@linkplain Task#ownsDurability() owns its durability} —
   * such tasks share nothing and commit directly via {@link #commit}.
   */
  public CompletableFuture<Void> commitSuspended(final Partition<R> partition, final long offset) {
    if (partition.task().ownsDurability()) {
      throw new IllegalArgumentException(
          "partition %d owns its durability — commit it directly, not through the persister"
              .formatted(partition.id()));
    }
    return cutPersister.submit(() -> commit(partition, offset));
  }

  /**
   * Stops the runtime-managed cut persister, draining any still-queued cuts and commit jobs first.
   * Called on runtime shutdown strictly <em>after</em> every partition actor has stopped, so the
   * stop commits routed through {@link #commitSuspended} are already drained (or, for a straggler
   * that outlived the shutdown wait, run inline by the persister's late-submit fallback).
   */
  public void close() {
    cutPersister.close();
  }

  /**
   * Commits {@code partition}'s work up to {@code offset} as one atomic cut: emit output, make it
   * durable, persist state and offset, then advance the source offset. Does only the durable work —
   * no partition bookkeeping — so it can run while the partition's actor is suspended
   * (single-writer over the task preserved by that suspension); the caller clears the pending
   * offset and resets the commit clock on the actor thread once this completes.
   *
   * <p>Threading: for a task that owns its durability this may run on any single thread (IO
   * executor or actor thread) — it shares nothing. For a runtime-managed task it runs only as a
   * {@link CutPersister} job (via {@link #commitSuspended}), because the branch below writes the
   * shared transaction runner, offset store and pre-commit flushes, and the persister thread is
   * their only writer.
   */
  public void commit(final Partition<R> partition, final long offset) {
    final Task<R> task = partition.task();
    task.flush();
    if (task.ownsDurability()) {
      // Self-contained shard: its own output, state and offset in its own transaction — no shared
      // state, so this runs fully in parallel with other partitions' commits.
      task.commit(offset);
    } else {
      // Shared runtime-managed durability — on the persister thread (see javadoc), so no lock.
      preCommitFlushes.forEach(Runnable::run);
      task.preCommitFlush();
      // Store the offset first, then checkpoint: if the offset store is write-back cached over the
      // task's backing store, the checkpoint flushes it in this same transaction, so state and
      // offset land as one atomic cut.
      transactionRunner.runInTransaction(
          () -> {
            offsets.store(partition.id(), offset);
            task.checkpoint();
          });
    }
    consumer.commitOffset(sourceTopic, partition.id(), offset).join();
  }
}
