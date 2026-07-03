/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming;

import io.camunda.eventbridge.client.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The produce-before-commit barrier, sharded by partition. Tracks the pending (highest processed)
 * offset per partition and, on {@link #commit()}, makes each partition an independent atomic cut:
 * its produced output is made durable, then its state and offset are persisted in that partition's
 * own transaction, then only that partition's source offset advances. A crash mid-loop replays the
 * not-yet-committed partitions rather than losing them. Single-writer (the runtime thread).
 *
 * @param <R> the decoded record type
 */
final class CommitBarrier<R> {

  private final PartitionTasks<R> tasks;
  private final Consumer consumer;
  private final String sourceTopic;
  private final TransactionRunner transactionRunner;
  private final OffsetStore offsets;
  private final List<Runnable> preCommitFlushes;
  private final Map<Integer, Long> pending = new HashMap<>();

  CommitBarrier(
      final PartitionTasks<R> tasks,
      final Consumer consumer,
      final String sourceTopic,
      final TransactionRunner transactionRunner,
      final OffsetStore offsets,
      final List<Runnable> preCommitFlushes) {
    this.tasks = tasks;
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
    this.transactionRunner = transactionRunner;
    this.offsets = offsets;
    this.preCommitFlushes = preCommitFlushes;
  }

  /**
   * Records the highest processed offset for {@code partition} to be committed on the next barrier.
   */
  void recordProcessed(final int partition, final long offset) {
    pending.merge(partition, offset, Math::max);
  }

  /** Commits every pending partition (see class javadoc). No-op when nothing was processed. */
  void commit() {
    if (pending.isEmpty()) {
      return;
    }
    // Emit every pending partition's output, then make it durable before any offset advances.
    // Global sinks run once (for tasks whose output is published elsewhere); a self-contained
    // sharded task instead flushes its own partition's output via Task#preCommitFlush.
    for (final int partition : pending.keySet()) {
      tasks.get(partition).flush();
    }
    preCommitFlushes.forEach(Runnable::run);
    for (final Map.Entry<Integer, Long> entry : pending.entrySet()) {
      commitPartition(entry.getKey(), entry.getValue());
    }
    pending.clear();
  }

  /**
   * Commits just {@code partition}'s pending work, if any — used when a partition is revoked, to
   * hand it off cleanly. Emits and flushes this partition's output, then commits it as one atomic
   * cut.
   */
  void commitRevoked(final int partition) {
    final Long offset = pending.remove(partition);
    if (offset == null) {
      return;
    }
    tasks.get(partition).flush();
    preCommitFlushes.forEach(Runnable::run);
    commitPartition(partition, offset);
  }

  /** The per-partition atomic cut: make the partition's output durable, persist state + offset. */
  private void commitPartition(final int partition, final long offset) {
    final Task<R> task = tasks.get(partition);
    if (task.ownsDurability()) {
      // The shard makes its own atomic cut: its output durable, then its state and offset in its
      // own transaction.
      task.commit(offset);
    } else {
      task.preCommitFlush();
      // Store the offset first, then checkpoint: if the offset store is write-back cached and
      // shares
      // the task's backing store, the checkpoint flushes it in this same transaction, so this
      // partition's state and offset land as one atomic cut.
      transactionRunner.runInTransaction(
          () -> {
            offsets.store(partition, offset);
            task.checkpoint();
          });
    }
    consumer.commitOffset(sourceTopic, partition, offset).join();
  }
}
