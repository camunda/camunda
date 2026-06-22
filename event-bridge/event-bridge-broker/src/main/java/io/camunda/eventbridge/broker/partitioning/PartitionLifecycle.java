/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.messaging.MessagingService;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.eventbridge.broker.partitioning.steps.EventStreamFetcherStep;
import io.camunda.eventbridge.broker.partitioning.steps.EventStreamStep;
import io.camunda.eventbridge.broker.partitioning.steps.FetchPurgatoryStep;
import io.camunda.eventbridge.broker.partitioning.steps.HighWatermarkStep;
import io.camunda.eventbridge.broker.partitioning.steps.LogStorageStep;
import io.camunda.eventbridge.broker.partitioning.steps.PollRequestHandlerStep;
import io.camunda.eventbridge.broker.partitioning.steps.PublishRequestHandlerStep;
import io.camunda.eventbridge.broker.partitioning.steps.TopologyStep;
import io.camunda.eventbridge.broker.transport.RequestHandlerRegistry;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.agrona.concurrent.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-partition actor that handles raft role changes. Tracks whether a transition is in progress
 * and defers new role changes until the current transition completes.
 */
public final class PartitionLifecycle extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(PartitionLifecycle.class);

  private final List<PartitionStartupStep> leaderSteps;
  private final PartitionContext context;

  private PartitionStartupSequence leaderSequence;
  private boolean isLeader;
  private boolean transitioning;
  private Role deferredRole;

  public PartitionLifecycle(
      final int partitionId,
      final int partitionCount,
      final RaftPartition raftPartition,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final InstantSource clock,
      final IdGenerator idGenerator,
      final TopologyManagerImpl topologyManager,
      final ExecutorService executorService) {
    context =
        new PartitionContext(
            partitionId,
            partitionCount,
            raftPartition,
            actorScheduler,
            messagingService,
            clock,
            idGenerator,
            executorService);
    context.setRequestHandlerRegistry(new RequestHandlerRegistry(partitionId, messagingService));
    // The consumer-group coordinator no longer runs on data partitions; it has its own Raft group
    // (see CoordinatorPartition), so its replicated offset log never pollutes the event stream.
    leaderSteps =
        List.of(
            new LogStorageStep(),
            new HighWatermarkStep(),
            new EventStreamStep(),
            new FetchPurgatoryStep(),
            new EventStreamFetcherStep(),
            new PublishRequestHandlerStep(),
            new PollRequestHandlerStep(),
            new TopologyStep(topologyManager));
  }

  @Override
  public String getName() {
    return "EventBridgePartition-" + context.getPartitionId();
  }

  @Override
  protected void onActorClosing() {
    if (isLeader) {
      isLeader = false;
      if (leaderSequence != null) {
        leaderSequence.closeAll();
      }
    }

    final var registry = context.getRequestHandlerRegistry();
    if (registry != null) {
      registry.close();
    }
  }

  public void onRoleChange(final Role role) {
    actor.submit(() -> handleRoleChange(role));
  }

  private void handleRoleChange(final Role role) {
    if (transitioning) {
      LOG.info(
          "Partition {} — transition in progress, deferring role change to {}",
          context.getPartitionId(),
          role);
      deferredRole = role;
      return;
    }

    switch (role) {
      case LEADER -> becomeLeader();
      default -> stepDown();
    }
  }

  private void onTransitionCompleted() {
    transitioning = false;

    if (deferredRole != null) {
      final var role = deferredRole;
      deferredRole = null;
      LOG.info(
          "Partition {} — processing deferred role change to {}", context.getPartitionId(), role);
      handleRoleChange(role);
    }
  }

  private void becomeLeader() {
    if (isLeader) {
      LOG.warn("Partition {} — already leader", context.getPartitionId());
      return;
    }

    LOG.info("Partition {} — becoming leader", context.getPartitionId());
    isLeader = true;
    transitioning = true;

    leaderSequence =
        new PartitionStartupSequence(
            leaderSteps, context, this::submitToActor, () -> isLeader, this::onTransitionCompleted);
    leaderSequence.start();
  }

  private void stepDown() {
    if (!isLeader) {
      return;
    }

    LOG.info("Partition {} — stepping down", context.getPartitionId());
    isLeader = false;

    if (leaderSequence != null) {
      transitioning = true;
      leaderSequence.closeAll();
    }
  }

  private void submitToActor(final Runnable task) {
    actor.submit(task);
  }

  public int getPartitionId() {
    return context.getPartitionId();
  }

  public boolean isLeader() {
    return isLeader;
  }
}
