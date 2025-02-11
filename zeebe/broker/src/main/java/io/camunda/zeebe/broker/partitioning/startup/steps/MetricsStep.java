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
import io.camunda.zeebe.util.micrometer.MicrometerUtil.PartitionKeyNames;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public class MetricsStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Partition Metrics Startup Step";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var brokerRegistry = context.brokerMeterRegistry();
    final var partitionRegistry = new CompositeMeterRegistry();
    final Integer partitionId = context.partitionMetadata().id().id();
    partitionRegistry
        .config()
        .commonTags(PartitionKeyNames.PARTITION.asString(), partitionId.toString());

    partitionRegistry.add(brokerRegistry);
    context.partitionMeterRegistry(partitionRegistry);

    return CompletableActorFuture.completed(context);
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var brokerRegistry = context.brokerMeterRegistry();
    final var partitionRegistry = context.partitionMeterRegistry();

    if (partitionRegistry != null) {
      partitionRegistry.clear();
      partitionRegistry.close();

      if (brokerRegistry instanceof final CompositeMeterRegistry parent) {
        parent.remove(partitionRegistry);
      }

      context.partitionMeterRegistry(null);
    }

    return CompletableActorFuture.completed(context);
  }
}
