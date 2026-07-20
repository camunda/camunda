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
import io.camunda.eventbridge.streaming.changelog.PartitionRoleController;
import io.camunda.eventbridge.streaming.changelog.PartitionRoleControllerFactory;
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
 * <p><b>Role-aware dispatch</b> (event-bridge-streaming ADR 0009 decision 6 / consumer-groups ADR
 * 0006 decision 1), active only when the runtime is built with a {@link
 * PartitionRoleControllerFactory}: a partition's {@link PartitionRoleController} — and the store it
 * owns — survives every role flip; only the driver on top of it changes.
 *
 * <ul>
 *   <li>A new STANDBY-target partition ({@code onStandbyPartitionsAssigned}) opens its controller
 *       and only tails its changelog ({@link #pollStandbies()}) — no {@link PartitionActor}, no
 *       source fetch.
 *   <li>A new ACTIVE assignment ({@code onPartitionsAssigned}) reuses the partition's controller if
 *       one is already warming as a standby, or opens one fresh, then promotes it ({@link
 *       PartitionRoleController#promote()}) — the identical cold-rebuild-then-fold path either way,
 *       so a promoted fold cannot tell itself apart from a restart. Only now does a {@link
 *       PartitionActor} exist for the partition, wrapping {@link
 *       PartitionRoleController#activeTask()}.
 *   <li>An ACTIVE revoke ({@code onPartitionsRevoked}) stops the actor cooperatively as always
 *       (final cut, then close); once it has stopped, the controller demotes ({@link
 *       PartitionRoleController#demote()} — {@link Task#closeKeepingStores()}, store left open) and
 *       either keeps tailing as a standby (still in this member's standby target) or is fully
 *       closed (no longer assigned in any role).
 *   <li>A STANDBY-target revoke ({@code onStandbyPartitionsRevoked}) for a partition not driven
 *       ACTIVE here closes its controller outright.
 * </ul>
 *
 * Without a {@link PartitionRoleControllerFactory} configured, none of the above runs: a partition
 * materializes straight through its {@link TaskFactory} exactly as before, byte for byte.
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
  // Nullable: role-aware dispatch (see the class javadoc) runs only when the application opts in.
  private final PartitionRoleControllerFactory<R> roleControllerFactory;

  private final Map<Integer, PartitionActor<R>> actors = new HashMap<>();
  private final Set<Integer> revoking = new HashSet<>();

  // Role-aware dispatch state (unused unless roleControllerFactory != null). A partition present in
  // roleControllers is this member's controller for it in whichever role it currently drives; an
  // ACTIVE entry also has a PartitionActor in `actors` wrapping the same controller's activeTask().
  private final Map<Integer, PartitionRoleController<?, R>> roleControllers = new HashMap<>();
  // This member's last-known standby target (a full set delivered by the coordinator, ADR 0009
  // decision 7), used only to decide — once a revoked ACTIVE partition's final cut completes —
  // whether to demote it to STANDBY (still in the target) or fully release it.
  private final Set<Integer> standbyTarget = new HashSet<>();
  private final Queue<Integer> newlyStandbyAssigned = new ConcurrentLinkedQueue<>();
  private final Queue<Integer> newlyStandbyRevoked = new ConcurrentLinkedQueue<>();

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
      final BooleanSupplier running,
      final PartitionRoleControllerFactory<R> roleControllerFactory) {
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
    this.roleControllerFactory = roleControllerFactory;
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

          @Override
          public void onStandbyPartitionsAssigned(final Collection<TopicPartition> partitions) {
            enqueue(newlyStandbyAssigned, partitions);
          }

          @Override
          public void onStandbyPartitionsRevoked(final Collection<TopicPartition> partitions) {
            enqueue(newlyStandbyRevoked, partitions);
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
        pollStandbies();
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
    applyStandbyRevocations();
    applyStandbyAssignments();
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

  /**
   * Drains standby-target revocations (a full-target delta, event-bridge-streaming ADR 0009
   * decision 6): a partition whose controller is still in the STANDBY role — i.e. not driven ACTIVE
   * here — is fully closed, since nothing on this member needs it any longer. A partition driven
   * ACTIVE here is left untouched; its standby-target membership matters only once its eventual
   * revoke completes (see {@link #demoteOrRelease}).
   */
  private void applyStandbyRevocations() {
    for (Integer partition; (partition = newlyStandbyRevoked.poll()) != null; ) {
      standbyTarget.remove(partition);
      final PartitionRoleController<?, R> controller = roleControllers.get(partition);
      if (controller != null && controller.role() == PartitionRoleController.Role.STANDBY) {
        closeController(partition, controller);
      }
    }
  }

  /**
   * Drains standby-target assignments: a partition new to this member's standby target opens its
   * controller in the STANDBY role — warming from the changelog start, or resuming from an intact
   * local position — and is never promoted here. A partition this member already drives (in either
   * role) is left alone; its controller already exists.
   */
  private void applyStandbyAssignments() {
    for (Integer partition; (partition = newlyStandbyAssigned.poll()) != null; ) {
      standbyTarget.add(partition);
      if (!roleControllers.containsKey(partition)) {
        roleControllers.put(partition, roleControllerFactory.startAsStandby(partition));
        LOG.info("Source loop '{}' warming standby for partition {}", instanceId, partition);
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
        demoteOrRelease(partition);
        LOG.info("Source loop '{}' released partition {}", instanceId, partition);
      }
    }
  }

  /**
   * Once a revoked ACTIVE partition's actor has stopped: if role-aware dispatch is off, or this
   * partition never had a controller, there is nothing further to do (today's behavior). Otherwise
   * hands the controller back to the STANDBY role ({@link PartitionRoleController#demote()} —
   * {@link Task#closeKeepingStores()}, the store stays open) and either keeps it warming (still in
   * this member's standby target) or fully closes it (no role left to play here).
   */
  private void demoteOrRelease(final int partition) {
    final PartitionRoleController<?, R> controller = roleControllers.get(partition);
    if (controller == null) {
      return;
    }
    if (controller.role() == PartitionRoleController.Role.ACTIVE) {
      controller.demote();
    }
    if (standbyTarget.contains(partition)) {
      LOG.info("Source loop '{}' demoted partition {} to standby", instanceId, partition);
    } else {
      closeController(partition, controller);
    }
  }

  /**
   * Closes a controller, defensively demoting it first if it is still ACTIVE. {@link
   * PartitionRoleController#close()} calls {@link Task#close()} — the FULL close, not {@link
   * Task#closeKeepingStores()} — whenever a task is present, and a task built to participate in
   * role changes closes its own provider from {@link Task#close()} exactly as it always has (see
   * e.g. {@code ProjectionStageTask#close()}); closing an ACTIVE controller directly would
   * therefore close the provider twice — once from that {@code close()}, once more from {@link
   * PartitionRoleController#close()} itself. Demoting first ({@link
   * PartitionRoleController#demote()} — {@link Task#closeKeepingStores()}, provider left open)
   * makes {@code role()} STANDBY (task {@code null}) before this ever calls {@code close()}, so the
   * provider closes exactly once. Every caller here reaches this already-demoted in practice
   * ({@link #demoteOrRelease} demotes before releasing, {@link #applyStandbyRevocations} only ever
   * touches a STANDBY controller) except {@link #closeRoleControllers}, shutdown's true teardown of
   * whatever role a controller was still in.
   */
  private void closeController(
      final int partition, final PartitionRoleController<?, R> controller) {
    if (controller.role() == PartitionRoleController.Role.ACTIVE) {
      controller.demote();
    }
    try {
      controller.close();
    } catch (final Exception e) {
      LOG.warn("Failed to close role controller for partition {}", partition, e);
    }
    roleControllers.remove(partition);
  }

  /**
   * Polls every controller currently in the STANDBY role once (event-bridge-streaming ADR 0009
   * decision 6): tails its changelog and applies whatever complete cuts have accumulated since the
   * last poll. Runs on the source thread alongside the source topic's own poll — a standby never
   * fetches or folds the source, so this is cheap relative to {@link #pollAndRoute()}. A no-op when
   * no {@link PartitionRoleControllerFactory} is configured (no controllers exist at all).
   */
  private void pollStandbies() {
    if (roleControllers.isEmpty()) {
      return;
    }
    for (final Map.Entry<Integer, PartitionRoleController<?, R>> entry :
        roleControllers.entrySet()) {
      final PartitionRoleController<?, R> controller = entry.getValue();
      if (controller.role() == PartitionRoleController.Role.STANDBY) {
        try {
          controller.pollStandby();
        } catch (final RuntimeException e) {
          LOG.warn(
              "Source loop '{}' failed polling standby partition {}",
              instanceId,
              entry.getKey(),
              e);
        }
      }
    }
  }

  /**
   * Closes every remaining role controller regardless of role — called once on runtime shutdown, a
   * true teardown rather than a role flip (unlike {@link #demoteOrRelease}, an ACTIVE controller
   * here is fully closed, not demoted: nothing will drive it further in this process).
   */
  public void closeRoleControllers() {
    for (final Map.Entry<Integer, PartitionRoleController<?, R>> entry :
        new ArrayList<>(roleControllers.entrySet())) {
      closeController(entry.getKey(), entry.getValue());
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
   *
   * <p>Role-aware (see the class javadoc): when a {@link PartitionRoleControllerFactory} is
   * configured, the task comes from the partition's {@link PartitionRoleController} instead of
   * {@link #taskFactory} — reused if the partition is already warming as a standby, opened fresh
   * otherwise — immediately {@link PartitionRoleController#promote}d either way, so the fold sees
   * the identical cold-rebuild-then-fold sequence regardless of which case this was.
   */
  private PartitionActor<R> materialize(final int partitionId) {
    // A fresh materialization must not inherit pause state from a previous incarnation (the
    // revoke path already cleared it — this is defensive).
    parked.remove(partitionId);
    if (paused.remove(partitionId)) {
      consumer.resume(List.of(new TopicPartition(sourceTopic, partitionId)));
    }
    final Task<R> task;
    final long baseline;
    if (roleControllerFactory != null) {
      PartitionRoleController<?, R> controller = roleControllers.get(partitionId);
      if (controller == null) {
        controller = roleControllerFactory.startAsStandby(partitionId);
        roleControllers.put(partitionId, controller);
      }
      baseline = controller.promote();
      task = controller.activeTask();
    } else {
      // The ownership epoch reads through to the consumer's live membership: a task samples it at
      // each commit barrier, so a fenced-and-rejoined member stamps its new epoch from then on.
      task = taskFactory.create(partitionId, consumer::memberEpoch);
      task.init();
      baseline = task.restore();
    }
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
