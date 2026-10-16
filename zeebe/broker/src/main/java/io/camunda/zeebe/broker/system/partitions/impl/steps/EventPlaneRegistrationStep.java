/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.eventplane.EventPlaneRegistration;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.jspecify.annotations.Nullable;

/**
 * Registers the physical tenant in its Event Plane while this broker leads one of the tenant's
 * partitions, if {@code camunda.event-plane.endpoint} is set. Every partition's leader registers
 * it, as each will publish the partition's log, so the tenant registers even while some of its
 * partitions have no leader. A registration changes nothing for an environment the Event Plane
 * knows, so the leaders' registrations, and new leaders', do no harm.
 *
 * <p>Each partition has its own instance of this step, which holds that partition's registration.
 * Registering is best effort: it never fails a transition.
 */
public final class EventPlaneRegistrationStep implements PartitionTransitionStep {

  private @Nullable EventPlaneRegistration registration;

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var running = registration;
    if (running == null) {
      return CompletableActorFuture.completed(null);
    }
    registration = null;
    return running.closeAsync();
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final var cfg = context.getBrokerCfg().getEventPlane();
    final var partitionId = context.partitionId();
    if (targetRole != Role.LEADER || cfg.getEndpoint() == null) {
      return CompletableActorFuture.completed(null);
    }

    final var configurationService = context.getClusterConfigurationService();
    final var started =
        new EventPlaneRegistration(
            cfg,
            partitionId,
            () -> configurationService.getCurrentClusterConfiguration().clusterId());
    registration = started;
    context
        .getConcurrencyControl()
        .runOnCompletion(
            context.getActorSchedulingService().submitActor(started),
            (ok, error) -> {
              if (error != null) {
                Loggers.SYSTEM_LOGGER.warn(
                    "Failed to start registering physical tenant {} in the Event Plane",
                    partitionId.group(),
                    error);
              }
            });
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "EventPlaneRegistration";
  }
}
