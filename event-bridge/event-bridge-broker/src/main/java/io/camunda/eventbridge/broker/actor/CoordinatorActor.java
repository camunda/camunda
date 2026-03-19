/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.actor;

import io.camunda.eventbridge.broker.coordinator.ConsumerGroupRegistry;
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
 * subscribe, heartbeat reception, dead-consumer eviction, and partition rebalancing.
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
    // Periodically scan for dead consumers
    final long heartbeatTimeout = properties.consumer().heartbeatTimeoutMs();
    actor.runAtFixedRate(Duration.ofMillis(heartbeatTimeout / 2), this::evictDeadConsumers);

    // Periodically compute the truncation boundary per partition and dispatch compaction
    // to partition leaders.
    final long truncationInterval = properties.broker().truncation().intervalMs();
    actor.runAtFixedRate(Duration.ofMillis(truncationInterval), this::runTruncation);
  }

  /**
   * Registers a consumer and triggers an immediate rebalance.
   *
   * @return a future resolving to the assigned partition IDs and the new generation
   */
  public ActorFuture<SubscribeResult> subscribe(final String groupId, final String consumerId) {
    final var result = new CompletableActorFuture<SubscribeResult>();
    actor.call(
        () -> {
          final var group = registry.subscribe(groupId, consumerId, totalPartitions);
          final List<Integer> assigned = group.getAssignedPartitions(consumerId);
          result.complete(new SubscribeResult(assigned, group.getGeneration()));
        });
    return result;
  }

  /**
   * Records a heartbeat for the given consumer.
   *
   * @return a future resolving to the current generation, or completing exceptionally if the
   *     consumer is not registered
   */
  public ActorFuture<Long> heartbeat(final String groupId, final String consumerId) {
    final var result = new CompletableActorFuture<Long>();
    actor.call(
        () -> {
          final boolean ok = registry.heartbeat(groupId, consumerId);
          if (ok) {
            final var group = registry.getGroup(groupId);
            result.complete(group != null ? group.getGeneration() : 0L);
          } else {
            result.completeExceptionally(new ConsumerNotRegisteredException(groupId, consumerId));
          }
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
          result.complete(
              new AssignmentResult(group.getAssignedPartitions(consumerId), group.getGeneration()));
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
    actor.call(
        () -> {
          final Set<ConsumerKey> aliveAssigned = new HashSet<>();
          for (final var entry : registry.getAllGroups().entrySet()) {
            final String groupId = entry.getKey();
            for (final String consumerId :
                entry.getValue().getAliveAssignedConsumersForPartition(partitionId)) {
              aliveAssigned.add(new ConsumerKey(groupId, consumerId));
            }
          }
          result.complete(offsetStore.getTruncationBoundary(partitionId, aliveAssigned));
        });
    return result;
  }

  private void evictDeadConsumers() {
    final long heartbeatTimeout = properties.consumer().heartbeatTimeoutMs();
    final Instant deadline = Instant.now().minus(Duration.ofMillis(heartbeatTimeout));
    registry.evictDeadConsumers(deadline, totalPartitions);
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

  public record SubscribeResult(List<Integer> assignedPartitions, long generation) {}

  public record AssignmentResult(List<Integer> assignedPartitions, long generation) {}

  public static final class ConsumerNotRegisteredException extends RuntimeException {
    ConsumerNotRegisteredException(final String groupId, final String consumerId) {
      super("Consumer not registered: groupId=" + groupId + ", consumerId=" + consumerId);
    }
  }
}
