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
import io.camunda.eventbridge.streaming.TaskFactory;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The source stage: the single plain thread that owns everything touching the consumer's fetch
 * cursor — applying assignment changes, polling, decoding, and routing decoded records into each
 * partition's queue for its actor to fold. It is deliberately not an actor: poll blocks, which an
 * actor must never do. Decoding here (ahead of folding) lets a single partition overlap its decode
 * with its processing; keeping poll and seek on one thread keeps repositioning race-free (a rebuild
 * seek and its queue reset happen between polls, so no pre-seek record is ever folded).
 *
 * <p><b>Per-partition back-pressure without head-of-line blocking.</b> Routing never blocks on a
 * full {@link PartitionQueue}: a refused offer pauses the partition on the consumer (excluding it
 * from subsequent polls) and parks the entry locally, so one slow partition back-pressures only
 * itself while every other partition keeps polling, routing, and folding. The parked backlog is
 * bounded — only the current poll batch can still carry records for a just-paused partition. Each
 * loop iteration flushes parked entries back into the queue and resumes the partition once the
 * backlog is drained and the queue is at least half free (hysteresis against pause/resume
 * flapping).
 *
 * <p>Rebalance callbacks fire on the client's heartbeat thread and only record the assignment
 * delta; this loop applies it — materialize a partition and submit its {@link PartitionActor}, or
 * request a revoked partition to stop and reap it once it has committed and closed. Materializing a
 * task whose shard restored no local state rebuilds it from the source start (change-log-free
 * handoff), skipping the stale pre-seek records of the current poll batch.
 *
 * @param <R> the decoded record type
 */
public final class SourceLoop<R> {

  private static final Logger LOG = LoggerFactory.getLogger(SourceLoop.class);

  /**
   * Poll cadence while any partition is paused, so a loop with nothing drainable (e.g. all owned
   * partitions paused) still re-checks its resume conditions promptly instead of sleeping the full
   * poll timeout.
   */
  private static final Duration PAUSED_POLL_TIMEOUT = Duration.ofMillis(25);

  private final Consumer consumer;
  private final String sourceTopic;
  private final String instanceId;
  private final MessageDeserializer<R> deserializer;
  private final RecordFilter recordFilter;
  private final ToLongFunction<byte[]> payloadTimestamps;
  private final TaskFactory<R> taskFactory;
  private final Function<Partition<R>, PartitionActor<R>> partitionActorFactory;
  private final int queueCapacity;
  private final int maxPoll;
  private final Duration pollTimeout;
  private final long errorBackoffMs;
  private final BooleanSupplier running;

  private final Map<Integer, PartitionActor<R>> actors = new HashMap<>();
  private final Set<Integer> revoking = new HashSet<>();

  // Partitions paused on the consumer because their queue refused an offer, and the decoded
  // entries parked while paused (in offset order). Only the current poll batch can still carry
  // records for a just-paused partition — later polls exclude it — so a parked deque is bounded
  // by one poll batch.
  private final Set<Integer> paused = new HashSet<>();
  private final Map<Integer, ArrayDeque<SourceEntry<R>>> parked = new HashMap<>();
  private final Queue<Integer> newlyAssigned = new ConcurrentLinkedQueue<>();
  private final Queue<Integer> newlyRevoked = new ConcurrentLinkedQueue<>();
  private final Set<Integer> rebuiltThisPoll = new HashSet<>();

  // Per-partition tail of a filter-rejected run within the current poll. A later accepted record
  // supersedes it (its commit covers the run); a tail still standing at the end of the poll is
  // flushed as ONE coalesced Filtered entry so the actor's commit position — and, when the
  // payload's event time can be peeked, stream time — advances past filtered-only stretches.
  private final Map<PartitionActor<R>, FilteredTail> filteredTails = new HashMap<>();

  /** The actors touched by the current poll (reused across polls, cleared per poll). */
  private final Set<PartitionActor<R>> touched = new LinkedHashSet<>();

  public SourceLoop(
      final Consumer consumer,
      final String sourceTopic,
      final String instanceId,
      final MessageDeserializer<R> deserializer,
      final RecordFilter recordFilter,
      final ToLongFunction<byte[]> payloadTimestamps,
      final TaskFactory<R> taskFactory,
      final Function<Partition<R>, PartitionActor<R>> partitionActorFactory,
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
    this.payloadTimestamps = payloadTimestamps;
    this.taskFactory = taskFactory;
    this.partitionActorFactory = partitionActorFactory;
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
        resumePaused();
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
      // A parked entry of a partition being handed off must never be folded here; the new owner
      // re-fetches from the committed offset. The client drops its pause mark with the revoked
      // partition, but resume defensively in case the revoke raced the pause.
      parked.remove(partition);
      if (paused.remove(partition)) {
        consumer.resume(List.of(new TopicPartition(sourceTopic, partition)));
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

  /**
   * Flushes parked entries head-first into their partition's queue and, once a paused partition's
   * backlog is drained and its queue is at least half free (hysteresis, so a partition does not
   * flap pause/resume on every freed slot), resumes it on the consumer.
   */
  private void resumePaused() {
    if (paused.isEmpty()) {
      return;
    }
    final Iterator<Integer> it = paused.iterator();
    while (it.hasNext()) {
      final int partitionId = it.next();
      final PartitionActor<R> actor = actors.get(partitionId);
      if (actor == null) {
        // Revoked and reaped while paused; applyRebalance already resumed the consumer.
        parked.remove(partitionId);
        it.remove();
        continue;
      }
      final ArrayDeque<SourceEntry<R>> backlog = parked.get(partitionId);
      boolean flushed = false;
      while (backlog != null && !backlog.isEmpty() && actor.tryOffer(backlog.peekFirst())) {
        backlog.pollFirst();
        flushed = true;
      }
      final boolean drained = backlog == null || backlog.isEmpty();
      if (drained) {
        parked.remove(partitionId);
        if (actor.queueRemainingCapacity() >= actor.queueCapacity() / 2) {
          consumer.resume(List.of(new TopicPartition(sourceTopic, partitionId)));
          it.remove();
          LOG.debug("Source loop '{}' resumed partition {}", instanceId, partitionId);
        }
      }
      if (flushed) {
        actor.signalWork();
      }
    }
  }

  private void pollAndRoute() {
    // While anything is paused, poll on a short timeout so the resume conditions are re-checked
    // promptly even when every drainable partition is idle.
    final Duration timeout =
        paused.isEmpty() || pollTimeout.compareTo(PAUSED_POLL_TIMEOUT) <= 0
            ? pollTimeout
            : PAUSED_POLL_TIMEOUT;
    final List<Event> events = consumer.poll(maxPoll, timeout);
    if (events.isEmpty()) {
      return;
    }
    rebuiltThisPoll.clear();
    filteredTails.clear(); // drop any tail a previous poll aborted on — losing an advance is safe
    touched.clear();
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
        // Filtered: skip decode and enqueue, but remember the run's tail so the commit position
        // still advances past it if no accepted record follows in this poll.
        final FilteredTail tail = filteredTails.computeIfAbsent(actor, a -> new FilteredTail());
        tail.offset = offset;
        if (payloadTimestamps != null) {
          tail.eventTimeMs =
              Math.max(tail.eventTimeMs, payloadTimestamps.applyAsLong(event.payload()));
        }
        continue;
      }
      filteredTails.remove(actor); // this record's commit covers any earlier filtered run
      routeOrPark(partitionId, actor, toEntry(event, offset));
    }
    if (!filteredTails.isEmpty()) {
      for (final Map.Entry<PartitionActor<R>, FilteredTail> entry : filteredTails.entrySet()) {
        final PartitionActor<R> actor = entry.getKey();
        final FilteredTail tail = entry.getValue();
        // Offset order holds when this parks: the tail offset exceeds every parked offset,
        // because a standing tail means no accepted record followed it in this poll.
        routeOrPark(actor.id(), actor, new SourceEntry.Filtered<>(tail.offset, tail.eventTimeMs));
      }
      filteredTails.clear();
    }
    touched.forEach(PartitionActor::signalWork);
  }

  /**
   * Routes one entry into its partition's queue, or parks it when the partition is paused or its
   * queue refuses the offer. A refusal pauses the partition on the consumer so subsequent polls
   * exclude it — the slow partition back-pressures only itself instead of blocking the loop.
   */
  private void routeOrPark(
      final int partitionId, final PartitionActor<R> actor, final SourceEntry<R> entry) {
    // While paused, always park — offering around a non-empty parked backlog would reorder.
    if (!paused.contains(partitionId) && actor.tryOffer(entry)) {
      touched.add(actor);
      return;
    }
    if (paused.add(partitionId)) {
      consumer.pause(List.of(new TopicPartition(sourceTopic, partitionId)));
      LOG.debug(
          "Source loop '{}' paused partition {} — its queue is full", instanceId, partitionId);
    }
    parked.computeIfAbsent(partitionId, id -> new ArrayDeque<>()).addLast(entry);
  }

  /** The mutable tail of one partition's filter-rejected run within the current poll. */
  private static final class FilteredTail {
    private long offset;
    private long eventTimeMs = Long.MIN_VALUE;
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
   * Materializes a partition's task, submits its actor, and registers it. A task whose shard
   * restored no local state is rebuilt from the source start (seek + queue reset), and the
   * partition is noted so this poll's stale pre-seek records for it are skipped.
   */
  private PartitionActor<R> materialize(final int partitionId) {
    // A fresh materialization must not inherit pause state from a previous incarnation (the
    // revoke path already cleared it — this is defensive).
    parked.remove(partitionId);
    if (paused.remove(partitionId)) {
      consumer.resume(List.of(new TopicPartition(sourceTopic, partitionId)));
    }
    // The ownership epoch reads through to the consumer's live membership: a task samples it at
    // each commit barrier, so a fenced-and-rejoined member stamps its new epoch from then on.
    final Task<R> task = taskFactory.create(partitionId, consumer::memberEpoch);
    task.init();
    final long baseline = task.restore();
    final PartitionQueue<R> queue = new PartitionQueue<>(queueCapacity);
    if (baseline == Task.NO_OFFSET) {
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
