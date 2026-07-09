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
import io.camunda.eventbridge.streaming.Task;
import java.util.concurrent.CompletableFuture;

/**
 * Makes one partition an independent atomic cut: emit its output, persist state and offset together
 * through the task's own transaction, then advance that partition's source offset. Every task is a
 * self-contained shard — nothing durable is shared across partitions — so cuts and commits of
 * different partitions run fully in parallel on whatever thread calls in here; single-writer over
 * the task is the caller's ({@link PartitionActor}'s) concern. Advancing the source offset uses the
 * thread-safe consumer and needs no coordination either.
 *
 * @param <R> the decoded record type
 */
public final class PartitionCommitter<R> {

  private final Consumer consumer;
  private final String sourceTopic;

  public PartitionCommitter(final Consumer consumer, final String sourceTopic) {
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
  }

  /**
   * Makes a frozen cut durable: publish its produced output, persist its state delta and {@code
   * offset} through the cut's own transaction, then send the source-offset advance and return a
   * future <em>without joining on it</em>. Runs on an IO thread while the partition's actor
   * <em>keeps folding</em> — the cut is detached from the live working state at the freeze barrier,
   * so no suspension is needed. The completion (retire or merge back) happens afterwards on the
   * actor thread, chained onto the returned future, not here.
   *
   * <p>The source-offset commit is advisory — the authoritative resume bookmark was just persisted
   * inside the cut's transaction — so it is sent after the transaction but not awaited: the IO
   * thread's occupancy ends here. The one hard ordering rule holds by construction: the commit is
   * never sent before the transaction committed.
   *
   * @return a future done only when the cut is fully complete — its transaction committed and its
   *     source-offset ack arrived
   */
  public CompletableFuture<Void> persistCut(
      final Partition<R> partition, final long offset, final CommitCut cut) {
    cut.publish();
    cut.persist();
    return consumer.commitOffset(sourceTopic, partition.id(), offset);
  }

  /**
   * Commits {@code partition}'s work up to {@code offset} as one synchronous atomic cut: emit
   * output, persist state and offset through the task's own transaction, then advance the source
   * offset. Used by the stop path and for tasks without frozen-cut support, while the partition's
   * actor is suspended (single-writer over the task preserved by that suspension). Does only the
   * durable work — no partition bookkeeping — so the caller clears the pending offset and resets
   * the commit clock on the actor thread once this completes. May run on any single thread (IO
   * executor or actor thread) — the shard shares nothing.
   */
  public void commit(final Partition<R> partition, final long offset) {
    final Task<R> task = partition.task();
    task.flush();
    task.commit(offset);
    consumer.commitOffset(sourceTopic, partition.id(), offset).join();
  }
}
