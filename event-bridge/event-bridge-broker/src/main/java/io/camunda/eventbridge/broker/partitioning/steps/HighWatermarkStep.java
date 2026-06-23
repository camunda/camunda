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
import io.camunda.eventbridge.pubsub.watermark.CommittedByteWatermark;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public class HighWatermarkStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "High Watermark";
  }

  @Override
  public void prepare(final PartitionContext context) {
    final var tracker = new CommittedByteWatermark(context.getPartitionId());
    context.setHighWatermark(tracker);
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    context.setHighWatermark(null);
    return CompletableActorFuture.completed(null);
  }
}
