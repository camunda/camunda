/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.internals;

import io.camunda.eventbridge.client.Consumer;
import io.camunda.eventbridge.client.Event;
import io.camunda.eventbridge.client.RebalanceListener;
import io.camunda.eventbridge.client.TopicPartition;
import io.camunda.eventbridge.streaming.MessageDeserializer;
import io.camunda.eventbridge.streaming.RecordFilter;
import io.camunda.eventbridge.streaming.Task;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The source stage: the single plain thread that owns everything touching the consumer's fetch
 * cursor — applying assignment changes, polling, decoding, and routing decoded records into each
 * partition's queue for its actor to fold. It is deliberately not an actor: poll and the bounded
 * {@link PartitionQueue#put} both block, which an actor must never do. Decoding here (ahead of
 * folding) lets a single partition overlap its decode with its processing; keeping poll and seek on
 * one thread keeps repositioning race-free (a rebuild seek and its queue reset happen between
 * polls, so no pre-seek record is ever folded).
 *
 * <p>Rebalance callbacks fire on the client's heartbeat thread and only record the assignment
 * delta; this loop applies it — materialize a partition and submit its {@link PartitionActor}, or
 * request a revoked partition to stop and reap it once it has committed and closed. Materializing a
 * task that owns its durability but has no local state rebuilds it from the source start
 * (change-log-free handoff), skipping the stale pre-seek records of the current poll batch.
 *
 * @param <R> the decoded record type
 */
public final class SourceLoop<R> {

  private static final Logger LOG = LoggerFactory.getLogger(SourceLoop.class);

  private final Consumer consumer;
  private final String sourceTopic;
  private final String instanceId;
  private final MessageDeserializer<R> deserializer;
  private final RecordFilter recordFilter;
  private final IntFunction<Task<R>> taskFactory;
  private final Function<Partition<R>, PartitionActor<R>> partitionActorFactory;
  private final Map<Integer, Long> restoredBaselines;
  private final int queueCapacity;
  private final int maxPoll;
  private final Duration pollTimeout;
  private final long errorBackoffMs;
  private final BooleanSupplier running;

  private final Map<Integer, PartitionActor<R>> actors = new HashMap<>();
  private final Set<Integer> revoking = new HashSet<>();
  private final Queue<Integer> newlyAssigned = new ConcurrentLinkedQueue<>();
  private final Queue<Integer> newlyRevoked = new ConcurrentLinkedQueue<>();
  private final Set<Integer> rebuiltThisPoll = new HashSet<>();

  public SourceLoop(
      final Consumer consumer,
      final String sourceTopic,
      final String instanceId,
      final MessageDeserializer<R> deserializer,
      final RecordFilter recordFilter,
      final IntFunction<Task<R>> taskFactory,
      final Function<Partition<R>, PartitionActor<R>> partitionActorFactory,
      final Map<Integer, Long> restoredBaselines,
      final int queueCapacity,
      final int maxPoll,
      final Duration pollTimeout,
      final long errorBackoffMs,
      final BooleanSupplier running) {
    this.consumer = consumer;
    this.sourceTopic = sourceTopic;
    this.instanceId = instanceId;
    this.deserializer = deserializer;
    this.recordFilter = recordFilter;
    this.taskFactory = taskFactory;
    this.partitionActorFactory = partitionActorFactory;
    this.restoredBaselines = restoredBaselines;
    this.queueCapacity = queueCapacity;
    this.maxPoll = maxPoll;
    this.pollTimeout = pollTimeout;
    this.errorBackoffMs = errorBackoffMs;
    this.running = running;
  }

  /**
   * Registers the rebalance listener that records assignment deltas for {@link #applyRebalance()}.
   * The callback runs on the heartbeat thread, so it only enqueues; the run loop applies the delta.
   */
  public void registerRebalanceListener() {
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

  /** Runs the source loop on the calling thread until {@code running} turns false. */
  public void run() {
    while (running.getAsBoolean()) {
      try {
        applyRebalance();
        reapStoppedRevoked();
        pollAndRoute();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (final RuntimeException e) {
        LOG.warn("Source loop '{}' failed; backing off {}ms", instanceId, errorBackoffMs, e);
        sleep(errorBackoffMs);
      }
    }
  }

  /** A snapshot of the currently owned partition actors, for shutdown. */
  public Collection<PartitionActor<R>> partitionActors() {
    return new ArrayList<>(actors.values());
  }

  private void applyRebalance() {
    for (Integer partition; (partition = newlyRevoked.poll()) != null; ) {
      final PartitionActor<R> actor = actors.get(partition);
      if (actor != null && revoking.add(partition)) {
        actor.requestStop(); // stops routing to it; it commits its last work and closes
      }
    }
    for (Integer partition; (partition = newlyAssigned.poll()) != null; ) {
      if (!actors.containsKey(partition)) {
        materialize(partition);
      }
    }
  }

  /** Reaps revoked partitions whose actor has finished its final commit and closed. */
  private void reapStoppedRevoked() throws InterruptedException {
    for (final Integer partition : new ArrayList<>(revoking)) {
      final PartitionActor<R> actor = actors.get(partition);
      if (actor == null || actor.awaitStopped(0)) {
        actors.remove(partition);
        revoking.remove(partition);
        LOG.info("Source loop '{}' released partition {}", instanceId, partition);
      }
    }
  }

  private void pollAndRoute() throws InterruptedException {
    final List<Event> events = consumer.poll(maxPoll, pollTimeout);
    if (events.isEmpty()) {
      return;
    }
    rebuiltThisPoll.clear();
    final Set<PartitionActor<R>> touched = new LinkedHashSet<>();
    for (final Event event : events) {
      final int partitionId = event.partitionId();
      if (revoking.contains(partitionId) || rebuiltThisPoll.contains(partitionId)) {
        continue; // being handed off, or its pre-seek records were discarded this poll
      }
      PartitionActor<R> actor = actors.get(partitionId);
      if (actor == null) {
        actor = materialize(partitionId);
        if (rebuiltThisPoll.contains(partitionId)) {
          continue; // just rebuilt: this record is pre-seek, re-fetched from the start next poll
        }
      }
      final long offset = event.position();
      if (offset <= actor.baseline()) {
        continue; // already folded into durable state — skip before the decode cost
      }
      if (!recordFilter.accept(event.payload())) {
        continue; // filtered: the processor does not fold this record — skip decode and enqueue
      }
      actor.offer(toEntry(event, offset)); // blocks when full — back-pressure
      touched.add(actor);
    }
    touched.forEach(PartitionActor::signalWork);
  }

  private SourceEntry<R> toEntry(final Event event, final long offset) {
    try {
      final R record = deserializer.deserialize(event.payload(), event.partitionId(), offset);
      return new SourceEntry.Decoded<>(offset, record);
    } catch (final RuntimeException e) {
      // Carry the failure to the actor so the exception policy is applied in offset order.
      return new SourceEntry.DecodeFailure<>(offset, e);
    }
  }

  /**
   * Materializes a partition's task, submits its actor, and registers it. A task that owns its
   * durability but restored no local state is rebuilt from the source start (seek + queue reset),
   * and the partition is noted so this poll's stale pre-seek records for it are skipped.
   */
  private PartitionActor<R> materialize(final int partitionId) {
    final Task<R> task = taskFactory.apply(partitionId);
    task.init();
    final long baseline =
        task.ownsDurability()
            ? task.restore()
            : restoredBaselines.getOrDefault(partitionId, Task.NO_OFFSET);
    final PartitionQueue<R> queue = new PartitionQueue<>(queueCapacity);
    if (task.ownsDurability() && baseline == Task.NO_OFFSET) {
      // Reassigned to a member with no local state for it: replay from the source start to rebuild
      // (change-log-free handoff). Discard any buffered/pre-seek records so none is processed.
      consumer.seekToBeginning(List.of(new TopicPartition(sourceTopic, partitionId)));
      queue.clear();
      rebuiltThisPoll.add(partitionId);
      LOG.info(
          "Source loop '{}' rebuilding partition {} from the source start",
          instanceId,
          partitionId);
    }
    final Partition<R> partition =
        new Partition<>(partitionId, queue, task, baseline, System.nanoTime());
    final PartitionActor<R> actor = partitionActorFactory.apply(partition);
    actors.put(partitionId, actor);
    return actor;
  }

  private void enqueue(final Queue<Integer> queue, final Collection<TopicPartition> partitions) {
    for (final TopicPartition tp : partitions) {
      if (sourceTopic.equals(tp.topic())) {
        queue.add(tp.partition());
      }
    }
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}
