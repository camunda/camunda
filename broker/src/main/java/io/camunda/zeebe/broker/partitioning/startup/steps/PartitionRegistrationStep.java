/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionHealthBroadcaster;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

public final class PartitionRegistrationStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Partition Registration";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    final var partitionId = context.partitionMetadata().id().id();
    final var zeebePartition = context.zeebePartition();
    final var topologyManager = context.topologyManager();
    zeebePartition.addFailureListener(
        new PartitionHealthBroadcaster(partitionId, topologyManager::onHealthChanged));
    context.diskSpaceUsageMonitor().addDiskUsageListener(zeebePartition);
    context.brokerHealthCheckService().registerMonitoredPartition(partitionId, zeebePartition);
    result.complete(context);
    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var partitionId = context.partitionMetadata().id().id();
    context.diskSpaceUsageMonitor().removeDiskUsageListener(context.zeebePartition());
    context.brokerHealthCheckService().removeMonitoredPartition(partitionId);
    // TODO: Find a way to remove the partition from the topology manager

    result.complete(context);
    return result;
  }
}
