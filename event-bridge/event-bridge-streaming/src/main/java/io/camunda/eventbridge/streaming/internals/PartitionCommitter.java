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
import java.util.concurrent.CompletableFuture;

/**
 * Makes one partition's frozen {@link CommitCut} durable as an independent atomic cut: publish its
 * produced output, persist its state delta and offset through the cut's own transaction, then
 * advance that partition's source offset. Every task is a self-contained shard — nothing durable is
 * shared across partitions — so cuts of different partitions run fully in parallel on whatever
 * thread calls in here; exclusive ownership of the frozen data is the caller's ({@link
 * PartitionActor}'s) concern. Advancing the source offset uses the thread-safe consumer and needs
 * no coordination either.
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
   * offset} through the cut's own transaction, then send the source-offset advance and return its
   * future <em>without joining on it</em> — the caller decides. The in-flight path runs this on an
   * IO thread while the partition's actor <em>keeps folding</em> (the cut is detached from the live
   * working state at the freeze barrier) and chains the completion; the stop path runs it inline on
   * the actor thread and joins, because shutdown legitimately waits for durability. The completion
   * (retire or merge back) is the caller's job either way, on the actor thread.
   *
   * <p>The source-offset commit is advisory — the authoritative resume bookmark was just persisted
   * inside the cut's transaction — so it is sent after the transaction but not awaited here. The
   * one hard ordering rule holds by construction: the commit is never sent before the transaction
   * committed.
   *
   * @return a future done only when the cut is fully durable — its transaction committed and its
   *     source-offset ack arrived
   */
  public CompletableFuture<Void> persistCut(
      final Partition<R> partition, final long offset, final CommitCut cut) {
    cut.publish();
    cut.persist();
    return consumer.commitOffset(sourceTopic, partition.id(), offset);
  }
}
