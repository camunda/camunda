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
 * Task#commit} and shares nothing — so partitions commit fully in parallel. A task that defers to
 * the runtime-managed durability shares the {@link TransactionRunner}, {@link OffsetStore}, and the
 * pre-commit flushes; those shared writes are serialized under one monitor so concurrent
 * per-partition commits stay correct. Advancing the source offset uses the thread-safe consumer and
 * needs no monitor.
 *
 * @param <R> the decoded record type
 */
public final class PartitionCommitter<R> {

  private final Consumer consumer;
  private final String sourceTopic;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;

  /** Serializes writes to the shared runtime-managed durability resources (see class javadoc). */
  private final Object sharedDurability = new Object();

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
  }

  /**
   * Makes a frozen cut durable: publish its produced output, persist its state delta and {@code
   * offset}, then send the source-offset advance and return its future <em>without joining on
   * it</em>. Runs on an IO thread while the partition's actor <em>keeps folding</em> — the cut is
   * detached from the live working state at the freeze barrier, so the IO thread owns it
   * exclusively and no suspension is needed. The completion (retire or merge back) happens
   * afterwards on the actor thread, chained onto the returned future, not here.
   *
   * @return the source-offset commit's future; the cut is fully complete only when it is done
   */
  public CompletableFuture<Void> persistCut(
      final Partition<R> partition, final long offset, final CommitCut cut) {
    if (partition.task().ownsDurability()) {
      // Self-contained shard: the cut publishes and persists through the task's own sinks and
      // transaction, fully in parallel with other partitions' cuts.
      cut.publish();
      cut.persist();
    } else {
      // Runtime-managed durability: shared resources are serialized, and the offset lands in the
      // same transaction as the frozen state — one atomic cut at the frozen barrier's offset.
      synchronized (sharedDurability) {
        preCommitFlushes.forEach(Runnable::run);
        cut.publish();
        transactionRunner.runInTransaction(
            () -> {
              offsets.store(partition.id(), offset);
              cut.persist();
            });
      }
    }
    // The source-offset commit is advisory — the authoritative resume bookmark was just persisted
    // inside the transaction — so it is sent after the transaction but not awaited: the IO
    // thread's occupancy ends here, and the caller chains the cut's completion onto the returned
    // future. The one hard ordering rule holds by construction: the commit is never sent before
    // the transaction committed.
    return consumer.commitOffset(sourceTopic, partition.id(), offset);
  }

  /**
   * Commits {@code partition}'s work up to {@code offset} as one atomic cut: emit output, make it
   * durable, persist state and offset, then advance the source offset. Does only the durable work —
   * no partition bookkeeping — so it can run on an IO thread while the partition's actor is
   * suspended (single-writer preserved by that suspension); the caller clears the pending offset
   * and resets the commit clock on the actor thread once this completes.
   */
  public void commit(final Partition<R> partition, final long offset) {
    final Task<R> task = partition.task();
    task.flush();
    if (task.ownsDurability()) {
      // Self-contained shard: its own output, state and offset in its own transaction — no shared
      // state, so this runs fully in parallel with other partitions' commits.
      task.commit(offset);
    } else {
      // Runtime-managed durability shares the transaction runner, offset store and pre-commit
      // flushes across partitions; serialize those writes so concurrent commits stay correct.
      synchronized (sharedDurability) {
        preCommitFlushes.forEach(Runnable::run);
        task.preCommitFlush();
        // Store the offset first, then checkpoint: if the offset store is write-back cached over
        // the
        // task's backing store, the checkpoint flushes it in this same transaction, so state and
        // offset land as one atomic cut.
        transactionRunner.runInTransaction(
            () -> {
              offsets.store(partition.id(), offset);
              task.checkpoint();
            });
      }
    }
    consumer.commitOffset(sourceTopic, partition.id(), offset).join();
  }
}
