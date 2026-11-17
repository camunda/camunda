/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps.recovery;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.partitioning.RecoveryPartitionStartupContext;
import io.camunda.zeebe.broker.system.monitoring.HealthMetrics;
import io.camunda.zeebe.broker.system.monitoring.HealthTreeMetrics;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;

/**
 * Wraps the broker meter registry with this partition's tags and registers the {@code zeebe.health}
 * gauge plus a recovering node in the component health tree for the duration of recovery mode,
 * mirroring {@link io.camunda.zeebe.broker.partitioning.startup.steps.MetricsStep} for the normal
 * partition pipeline. Must run first on startup and last on shutdown so the gauges are unregistered
 * before the partition manager stops - otherwise a partition that later starts processing again
 * would register new gauges under the same names and tags, which Micrometer silently ignores in
 * favor of the stale ones left behind here.
 */
public record RecoveryMetricsStep(PartitionId partitionId)
    implements StartupStep<RecoveryPartitionStartupContext> {

  @Override
  public String getName() {
    return "Recovery Partition %d - Metrics".formatted(partitionId.number());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> startup(
      final RecoveryPartitionStartupContext context) {
    final var partitionRegistry =
        MicrometerUtil.wrap(context.meterRegistry(), PartitionKeyNames.tags(partitionId));
    final var healthMetrics = new HealthMetrics(partitionRegistry);

    // Reuse the component name and parent of the normal ZeebePartition so the partition keeps its
    // row in the component health tree while it is recovering, instead of vanishing from it.
    final var healthTreeMetrics = new HealthTreeMetrics(partitionRegistry);
    final var componentName = ZeebePartition.componentName(partitionId);
    healthTreeMetrics.registerRelationship(componentName, context.brokerComponentName());
    healthTreeMetrics.registerRecoveringNode(componentName);

    return CompletableActorFuture.completed(
        context
            .setPartitionMeterRegistry(partitionRegistry)
            .setHealthMetrics(healthMetrics)
            .setHealthTreeMetrics(healthTreeMetrics));
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> shutdown(
      final RecoveryPartitionStartupContext context) {
    final var healthTreeMetrics = context.getHealthTreeMetrics();
    if (healthTreeMetrics != null) {
      healthTreeMetrics.close();
    }
    MicrometerUtil.close(context.getPartitionMeterRegistry());
    return CompletableActorFuture.completed(
        context.setPartitionMeterRegistry(null).setHealthMetrics(null).setHealthTreeMetrics(null));
  }
}
