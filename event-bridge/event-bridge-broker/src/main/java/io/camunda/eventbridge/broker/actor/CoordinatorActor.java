/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry;
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.AckStatus;
import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry.HeartbeatDelta;
import io.camunda.eventbridge.broker.offset.OffsetStore;
import io.camunda.eventbridge.broker.offset.OffsetStore.ConsumerKey;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Actor running on the coordinator broker (Broker-0) that manages all consumer group state:
 * heartbeat reception (with auto-registration), ACK processing, dead-consumer eviction, and
 * partition rebalancing.
 *
 * <p>All state mutations happen on this actor's thread; no external synchronization is needed.
 */
public final class CoordinatorActor extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(CoordinatorActor.class);

  private final ConsumerGroupRegistry registry;
  private final OffsetStore offsetStore;
  private final EventBridgeProperties properties;
  private final int totalPartitions;

  /**
   * Partition actors keyed by partition ID. Used by the truncation timer to dispatch compaction
   * requests to each partition leader after computing the min-committed-offset boundary. May be
   * empty (e.g., in unit-test contexts that do not wire RAFT partitions).
   */
  private final Map<Integer, PublishActor> publishActors;

  /**
   * Tracks the last truncation boundary sent to each partition, so we only dispatch a {@link
   * PublishActor#truncate(long)} call when the boundary actually advances.
   */
  private final Map<Integer, Long> lastTruncatedBoundary = new HashMap<>();

  public CoordinatorActor(
      final ConsumerGroupRegistry registry,
      final OffsetStore offsetStore,
      final EventBridgeProperties properties,
      final int totalPartitions) {
    this(registry, offsetStore, properties, totalPartitions, Map.of());
  }

  public CoordinatorActor(
      final ConsumerGroupRegistry registry,
      final OffsetStore offsetStore,
      final EventBridgeProperties properties,
      final int totalPartitions,
      final Map<Integer, PublishActor> publishActors) {
    this.registry = registry;
    this.offsetStore = offsetStore;
    this.properties = properties;
    this.totalPartitions = totalPartitions;
    this.publishActors = Map.copyOf(publishActors);
  }

  @Override
  public String getName() {
    return "event-bridge-coordinator";
  }

  @Override
  protected void onActorStarted() {
    // Periodically run the full coordinator loop: ACK timeouts, dead-consumer eviction, partition
    // count reconciliation, and BALANCED_STICKY rebalancing.
    final long rebalanceInterval = properties.consumer().rebalanceIntervalMs();
    actor.runAtFixedRate(Duration.ofMillis(rebalanceInterval), this::runCoordinatorLoop);

    // Periodically compute the truncation boundary per partition and dispatch compaction
    // to partition leaders.
    final long truncationInterval = properties.broker().truncation().intervalMs();
    actor.runAtFixedRate(Duration.ofMillis(truncationInterval), this::runTruncation);
  }

  /**
   * Records a heartbeat for the given consumer, auto-registering it on first contact.
   *
   * <p>The response contains the coordinator epoch and the partition delta (revoke/assign) or, if
   * the consumer is behind, a full assignment list for reconciliation.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param clientEpoch the epoch last seen by the consumer; {@code 0} for a new consumer
   * @param ownedPartitions partition IDs the consumer currently holds
   * @return a future resolving to the heartbeat result
   */
  public ActorFuture<HeartbeatResult> heartbeat(
      final String groupId,
      final String consumerId,
      final long clientEpoch,
      final List<Integer> ownedPartitions) {
    final var result = new CompletableActorFuture<HeartbeatResult>();
    actor.call(
        () -> {
          final HeartbeatDelta delta =
              registry.heartbeat(
                  groupId,
                  consumerId,
                  clientEpoch,
                  new HashSet<>(ownedPartitions),
                  totalPartitions);
          result.complete(
              new HeartbeatResult(
                  delta.epoch(), delta.revoke(), delta.assign(), delta.fullAssignment()));
        });
    return result;
  }

  /**
   * Processes a consumer ACK confirming revoked and assigned partitions.
   *
   * <p>If the ACK epoch is stale the coordinator logs a warning and returns {@code OK} without
   * mutating state; the consumer self-corrects on the next heartbeat.
   *
   * @param groupId consumer group identifier
   * @param consumerId consumer identifier
   * @param epoch the coordinator epoch from the heartbeat response that triggered this ACK
   * @param revoked partition IDs the consumer has stopped processing
   * @param assigned partition IDs the consumer has started processing
   * @return a future resolving to the ACK result
   */
  public ActorFuture<AckResult> ack(
      final String groupId,
      final String consumerId,
      final long epoch,
      final List<Integer> revoked,
      final List<Integer> assigned) {
    final var result = new CompletableActorFuture<AckResult>();
    actor.call(
        () -> {
          final AckStatus status = registry.ack(groupId, consumerId, epoch, revoked, assigned);
          result.complete(new AckResult(status));
        });
    return result;
  }

  /**
   * Commits an offset for a consumer on a partition.
   *
   * <p>The consumer must be currently active (alive) in the registry; the committed position is
   * stored in the {@link OffsetStore}. Idempotent: commits at or below the currently stored value
   * are silently accepted.
   *
   * @return a future that completes when the commit is recorded, or exceptionally if the consumer
   *     is not registered
   */
  public ActorFuture<Void> commitOffset(
      final String groupId, final String consumerId, final int partitionId, final long position) {
    final var result = new CompletableActorFuture<Void>();
    actor.call(
        () -> {
          if (!registry.isConsumerActive(groupId, consumerId)) {
            result.completeExceptionally(new ConsumerNotRegisteredException(groupId, consumerId));
            return;
          }
          offsetStore.commit(groupId, consumerId, partitionId, position);
          result.complete(null);
        });
    return result;
  }

  /** Returns the partition assignments for the given consumer's group. */
  public ActorFuture<AssignmentResult> getAssignment(
      final String groupId, final String consumerId) {
    final var result = new CompletableActorFuture<AssignmentResult>();
    actor.call(
        () -> {
          final var group = registry.getGroup(groupId);
          if (group == null) {
            result.completeExceptionally(new ConsumerNotRegisteredException(groupId, consumerId));
            return;
          }
          if (!registry.isConsumerActive(groupId, consumerId)) {
            result.completeExceptionally(new ConsumerNotRegisteredException(groupId, consumerId));
            return;
          }
          result.complete(
              new AssignmentResult(group.getAssignedPartitions(consumerId), group.getEpoch()));
        });
    return result;
  }

  /**
   * Computes the truncation boundary for the given partition: the minimum committed offset across
   * all consumers that are currently alive and assigned to that partition, across all groups.
   *
   * <p>Returns {@link Long#MAX_VALUE} if no eligible consumer has ever committed to this partition
   * (no truncation should occur).
   *
   * @return a future resolving to the truncation boundary position
   */
  public ActorFuture<Long> getTruncationBoundary(final int partitionId) {
    final var result = new CompletableActorFuture<Long>();
    actor.call(() -> result.complete(computeTruncationBoundary(partitionId)));
    return result;
  }

  /**
   * Coordinator loop callback: runs at {@code rebalanceIntervalMs} on the actor thread.
   *
   * <p>Execution order per cycle:
   *
   * <ol>
   *   <li><b>ACK timeout eviction</b> — consumers that failed to ACK within {@code ackTimeoutMs}
   *       are evicted (same treatment as session timeout). Their partitions are freed for
   *       redistribution.
   *   <li><b>Session-timeout eviction</b> — consumers with no heartbeat within {@code
   *       sessionTimeoutMs} are evicted. Both eviction types set the group's {@code
   *       consumersChanged} flag; the rebalance is triggered inside {@link
   *       ConsumerGroupRegistry#evictDeadConsumers} when the flag is true.
   *   <li><b>Partition-count reconciliation</b> — detected implicitly inside {@link
   *       ConsumerGroupRegistry#evictDeadConsumers}: if a group's tracked partition set no longer
   *       matches its {@code configuredPartitionCount}, a rebalance is triggered.
   * </ol>
   */
  private void runCoordinatorLoop() {
    final Instant now = Instant.now();

    // Step 1 (spec step 2): evict consumers whose ACK deadline has passed.
    registry.expireAckTimeouts(now);

    // Step 2 (spec step 3 + 4): evict session-timed-out consumers and trigger rebalance for any
    // group with pending membership changes (including ACK evictions from step 1) or partition
    // count mismatches.
    final long sessionTimeout = properties.consumer().sessionTimeoutMs();
    final Instant sessionDeadline = now.minus(Duration.ofMillis(sessionTimeout));
    registry.evictDeadConsumers(sessionDeadline, totalPartitions);
  }

  /**
   * Periodic timer callback: for each partition, compute the truncation boundary (min committed
   * offset across alive assigned consumers) and dispatch a {@link PublishActor#truncate} call to
   * the partition's actor when the boundary advances beyond the last value sent.
   *
   * <p>Runs on the coordinator actor's thread. The boundary computation is entirely in-memory
   * (reads from {@link ConsumerGroupRegistry} and {@link OffsetStore}). Dispatch to the partition
   * actor is non-blocking: {@code PublishActor.truncate()} schedules the compaction on the RAFT
   * thread context and returns immediately.
   *
   * <p>If {@code publishActors} is empty (e.g., in unit-test contexts without RAFT), this method is
   * a no-op.
   */
  private void runTruncation() {
    if (publishActors.isEmpty()) {
      return;
    }
    for (int partitionId = 0; partitionId < totalPartitions; partitionId++) {
      final long boundary = computeTruncationBoundary(partitionId);
      if (boundary == Long.MAX_VALUE) {
        // No alive consumer has committed on this partition — retain all log entries.
        continue;
      }
      final long last = lastTruncatedBoundary.getOrDefault(partitionId, Long.MIN_VALUE);
      if (boundary <= last) {
        // Boundary has not advanced; no need to issue another truncation request.
        continue;
      }
      final PublishActor publishActor = publishActors.get(partitionId);
      if (publishActor == null) {
        continue;
      }
      lastTruncatedBoundary.put(partitionId, boundary);
      LOG.debug(
          "Dispatching truncation for partition {} up to position {} (prev={})",
          partitionId,
          boundary,
          last);
      publishActor.truncate(boundary);
    }
  }

  /**
   * Computes the truncation boundary for {@code partitionId} synchronously (on the actor thread).
   * Equivalent to {@link #getTruncationBoundary(int)} but without the actor-call wrapping.
   */
  private long computeTruncationBoundary(final int partitionId) {
    final Set<ConsumerKey> aliveAssigned = new HashSet<>();
    for (final var entry : registry.getAllGroups().entrySet()) {
      final String groupId = entry.getKey();
      for (final String consumerId :
          entry.getValue().getAliveAssignedConsumersForPartition(partitionId)) {
        aliveAssigned.add(new ConsumerKey(groupId, consumerId));
      }
    }
    return offsetStore.getTruncationBoundary(partitionId, aliveAssigned);
  }

  // -------------------------------------------------------------------------
  // Result types

  /**
   * Result of a {@link #heartbeat} call.
   *
   * @param epoch coordinator's current epoch
   * @param revoke partitions the consumer must stop processing and ACK
   * @param assign partitions the consumer should start processing and ACK
   * @param fullAssignment non-empty when the consumer is behind (clientEpoch &lt; epoch); the
   *     consumer must reconcile fully from this list
   */
  public record HeartbeatResult(
      long epoch, List<Integer> revoke, List<Integer> assign, List<Integer> fullAssignment) {}

  /**
   * Result of an {@link #ack} call.
   *
   * @param status {@link AckStatus#OK} in the normal case; the coordinator returns {@code OK} even
   *     for stale-epoch ACKs to avoid surfacing errors to the consumer
   */
  public record AckResult(AckStatus status) {}

  /**
   * @deprecated Use {@link HeartbeatResult#epoch()} instead.
   */
  @Deprecated
  public record AssignmentResult(List<Integer> assignedPartitions, long epoch) {}

  public static final class ConsumerNotRegisteredException extends RuntimeException {
    public ConsumerNotRegisteredException(final String groupId, final String consumerId) {
      super("Consumer not registered: groupId=" + groupId + ", consumerId=" + consumerId);
    }
  }
}
