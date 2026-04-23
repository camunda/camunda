/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning.steps;

import io.camunda.eventbridge.broker.flowcontrol.CompositeFlowControl;
import io.camunda.eventbridge.broker.flowcontrol.InFlightLimiter;
import io.camunda.eventbridge.broker.logstreams.EventBridgeEventStream;
import io.camunda.eventbridge.broker.partitioning.PartitionContext;
import io.camunda.eventbridge.broker.partitioning.PartitionStartupStep;
import io.camunda.eventbridge.broker.transport.publish.PublishRequestCorrelator;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class EventStreamStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "EventStream";
  }

  @Override
  public void prepare(final PartitionContext context) {
    final var correlator = new PublishRequestCorrelator(context.getIdGenerator());
    context.setCorrelator(correlator);

    context.setEventStream(
        EventBridgeEventStream.builder()
            .partitionId(context.getPartitionId())
            .logStorage(context.getLogStorage())
            .listener(correlator)
            .actorScheduler(context.getActorScheduler())
            .clock(context.getClock())
            .flowControl(new CompositeFlowControl(new InFlightLimiter(8192)))
            .highWatermark(context.getHighWatermark())
            .build());
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    return context.getEventStream().openAsync();
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var stream = context.getEventStream();
    context.setEventStream(null);
    context.setCorrelator(null);

    if (stream != null) {
      return stream.closeAsync();
    }

    return CompletableActorFuture.completed(null);
  }
}
