/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.streaming.Task;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges the consumer's rebalance callbacks to the runtime thread. The callback fires off-thread
 * (the heartbeat), so it only records the assignment delta into concurrent queues; {@link #apply()}
 * drains them on the run thread so all task materialization/release stays single-threaded. On
 * acquire it materializes the partition's task and, for a self-owning shard with no local state,
 * rewinds it to the source start to rebuild (the change-log-free handoff — ADR 0002); on release it
 * commits the partition's last work and closes its task.
 *
 * @param <R> the decoded record type
 */
public final class RebalanceCoordinator<R> {

  private static final Logger LOG = LoggerFactory.getLogger(RebalanceCoordinator.class);

  private final PartitionTasks<R> tasks;
  private final CommitBarrier<R> commitBarrier;
  private final PunctuationDriver<R> punctuator;
  private final Consumer consumer;
  private final String sourceTopic;
  private final String instanceId;

  private final Queue<Integer> newlyAssigned = new ConcurrentLinkedQueue<>();
  private final Queue<Integer> newlyRevoked = new ConcurrentLinkedQueue<>();

  public RebalanceCoordinator(
      final PartitionTasks<R> tasks,
      final CommitBarrier<R> commitBarrier,
      final PunctuationDriver<R> punctuator,
      final Consumer consumer,
      final String sourceTopic,
      final String instanceId) {
    this.tasks = tasks;
    this.commitBarrier = commitBarrier;
    this.punctuator = punctuator;
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
    this.instanceId = instanceId;
  }

  /** Registers the listener so assignment deltas are recorded for {@link #apply()} to process. */
  public void register() {
    consumer.rebalanceListener(
        new RebalanceListener() {
          @Override
          public void onPartitionsAssigned(final Collection<TopicPartition> partitions) {
            enqueue(newlyAssigned, partitions);
          }

          @Override
          public void onPartitionsRevoked(final Collection<TopicPartition> partitions) {
            enqueue(newlyRevoked, partitions);
          }
        });
  }

  private void enqueue(final Queue<Integer> queue, final Collection<TopicPartition> partitions) {
    for (final TopicPartition tp : partitions) {
      if (sourceTopic.equals(tp.topic())) {
        queue.add(tp.partition());
      }
    }
  }

  /**
   * Applies pending deltas on the run thread: release revoked partitions, then acquire new ones.
   */
  public void apply() {
    for (Integer partition; (partition = newlyRevoked.poll()) != null; ) {
      release(partition);
    }
    for (Integer partition; (partition = newlyAssigned.poll()) != null; ) {
      acquire(partition);
    }
  }

  private void acquire(final int partition) {
    final Task<R> task = tasks.taskFor(partition);
    if (task.ownsDurability() && tasks.baseline(partition) == Task.NO_OFFSET) {
      // A shard reassigned to a member with no local state for it: replay the source from the start
      // to rebuild (the change-log-free handoff — see ADR 0002).
      consumer.seekToBeginning(List.of(new TopicPartition(sourceTopic, partition)));
      LOG.info(
          "Stream runtime '{}' rebuilding partition {} from the source start",
          instanceId,
          partition);
    }
  }

  private void release(final int partition) {
    commitBarrier.commitRevoked(partition); // commit its last work before handing it off
    final Task<R> task = tasks.release(partition);
    if (task != null) {
      task.close();
    }
    punctuator.forget(partition);
  }
}
