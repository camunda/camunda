/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.coordinator.CoordinationManager;
import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.eventbridge.broker.transport.coordinator.CoordinationRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class CoordinatorRequestHandlerStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "Coordination";
  }

  @Override
  public void prepare(final PartitionContext context) {
    final var mgr =
        new CoordinationManager(
            context.getPartitionId(),
            context.getPartitionCount(),
            context.getClock(),
            context.getRaftPartition().dataDirectory().toPath());
    context.setCoordinationManager(mgr);
    context.setCoordinationRequestHandler(
        new CoordinationRequestHandler(context.getPartitionId(), mgr));
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var future = context.getActorScheduler().submitActor(context.getCoordinationManager());
    final var topic = CoordinationRequestHandler.topicName(context.getPartitionId());
    context.getRequestHandlerRegistry().register(topic, context.getCoordinationRequestHandler());
    return future;
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var topic = CoordinationRequestHandler.topicName(context.getPartitionId());

    if (context.getRequestHandlerRegistry() != null) {
      context.getRequestHandlerRegistry().unregister(topic);
    }
    final var mgr = context.getCoordinationManager();
    context.setCoordinationManager(null);
    context.setCoordinationRequestHandler(null);

    if (mgr != null) {
      return mgr.closeAsync();
    }

    return CompletableActorFuture.completed(null);
  }
}
