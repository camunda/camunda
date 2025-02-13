/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;

public class MetricsStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Partition Metrics Startup Step";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var brokerRegistry = context.brokerMeterRegistry();
    final var partitionId = context.partitionMetadata().id().id();
    final var partitionRegistry =
        MicrometerUtil.wrap(brokerRegistry, PartitionKeyNames.tags(partitionId));

    context.partitionMeterRegistry(partitionRegistry);
    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var partitionRegistry = context.partitionMeterRegistry();

    if (partitionRegistry != null) {
      MicrometerUtil.discard(partitionRegistry);
      context.partitionMeterRegistry(null);
    }

    return CompletableActorFuture.completed(context);
  }
}
