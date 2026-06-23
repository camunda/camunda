/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.eventbridge.pubsub.transport.publish.PublishRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class PublishRequestHandlerStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "PublishHandler";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var handler =
        new PublishRequestHandler(
            context.getPartitionId(),
            context.getEventStream().getWriter(),
            context.getCorrelator());
    context.setPublishRequestHandler(handler);

    final var topic = PublishRequestHandler.topicName(context.getPartitionId());
    context.getRequestHandlerRegistry().register(topic, handler);

    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var topic = PublishRequestHandler.topicName(context.getPartitionId());
    if (context.getRequestHandlerRegistry() != null) {
      context.getRequestHandlerRegistry().unregister(topic);
    }

    context.setPublishRequestHandler(null);

    return CompletableActorFuture.completed(null);
  }
}
