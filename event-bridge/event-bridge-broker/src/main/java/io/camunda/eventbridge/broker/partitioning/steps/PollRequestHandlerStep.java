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
import io.camunda.eventbridge.messaging.stream.EventStreamReader;
import io.camunda.eventbridge.messaging.transport.poll.PollRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.function.Supplier;

/** Registers the copy-based poll handler ({@code poll-api-<partition>}) on the partition leader. */
public final class PollRequestHandlerStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "Poll Handler";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final int partitionId = context.getPartitionId();
    final Supplier<EventStreamReader> readerFactory = context.getEventStream()::newReader;
    final var handler = new PollRequestHandler(partitionId, readerFactory);
    context
        .getRequestHandlerRegistry()
        .register(PollRequestHandler.topicName(context.getRoutingGroup(), partitionId), handler);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    if (context.getRequestHandlerRegistry() != null) {
      context
          .getRequestHandlerRegistry()
          .unregister(
              PollRequestHandler.topicName(context.getRoutingGroup(), context.getPartitionId()));
    }
    return CompletableActorFuture.completed(null);
  }
}
