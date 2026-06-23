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
import io.camunda.eventbridge.pubsub.fetch.FetchPurgatory;
import io.camunda.eventbridge.pubsub.watermark.CommittedByteWatermark;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.time.InstantSource;

public class FetchPurgatoryStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "Fetch Purgatory";
  }

  @Override
  public ActorFuture<Void> activate(final PartitionContext context) {
    final var purgatory =
        new FetchPurgatory(
            context.getPartitionId(), context.getHighWatermark(), InstantSource.system());
    context.setFetchPurgatory(purgatory);
    ((CommittedByteWatermark) context.getHighWatermark())
        .setOnWatermarkAdvancedNotifier(purgatory::onWatermarkAdvanced);
    return context.getActorScheduler().submitActor(purgatory);
  }

  @Override
  public ActorFuture<Void> deactivate(final PartitionContext context) {
    final var purgatory = context.getFetchPurgatory();
    context.setFetchPurgatory(null);

    if (purgatory != null) {
      return purgatory.closeAsync();
    }

    return CompletableActorFuture.completed(null);
  }
}
