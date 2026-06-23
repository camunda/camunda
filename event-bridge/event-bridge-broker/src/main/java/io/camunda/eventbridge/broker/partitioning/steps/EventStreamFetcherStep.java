/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import static io.camunda.eventbridge.pubsub.transport.fetch.FetchRequestHandler.topicName;

import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.eventbridge.pubsub.fetch.EventStreamFetcher;
import io.camunda.eventbridge.pubsub.flowcontrol.SemaphoreFlowControl;
import io.camunda.eventbridge.pubsub.stream.EventStreamReader;
import io.camunda.eventbridge.pubsub.transport.fetch.FetchRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.InstantSource;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class EventStreamFetcherStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "Fetch Stream";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final Supplier<EventStreamReader> readerFactory = context.getEventStream()::newReader;
    final var executor = context.getExecutorService();
    final var highWatermark = context.getHighWatermark();
    final var eventStreamFetcher =
        new EventStreamFetcher(
            context.getPartitionId(),
            readerFactory,
            executor,
            null,
            highWatermark,
            new SemaphoreFlowControl(new Semaphore(32)),
            InstantSource.system(),
            4 * 1024 * 1024);
    final var partitionId = context.getPartitionId();
    final var requestHandler = new FetchRequestHandler(partitionId, eventStreamFetcher);
    final var topicName = topicName(partitionId);

    context.getFetchPurgatory().setFetchDispatcher(eventStreamFetcher::dispatchFetch);
    context.getFetchPurgatory().setTimeoutHandler(eventStreamFetcher::dispatchFetch);
    context.getRequestHandlerRegistry().registerWithManagedPayload(topicName, requestHandler);

    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var topicName = topicName(context.getPartitionId());
    if (context.getRequestHandlerRegistry() != null) {
      context.getRequestHandlerRegistry().unregister(topicName);
    }

    context.setFetchRequestHandler(null);
    return CompletableActorFuture.completed(null);
  }
}
