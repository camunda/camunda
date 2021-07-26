/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.SnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.NoneSnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.StateReplication;
import java.util.concurrent.CompletableFuture;

public class SnapshotReplicationPartitionStartupStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "SnapshotReplication";
  }

  @Override
  public CompletableFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final SnapshotReplication replication =
        shouldReplicateSnapshots(context)
            ? new StateReplication(
                context.getMessagingService(), context.getPartitionId(), context.getNodeId())
            : new NoneSnapshotReplication();

    context.setSnapshotReplication(replication);
    return CompletableFuture.completedFuture(context);
  }

  @Override
  public CompletableFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext context) {
    try {
      if (context.getSnapshotReplication() != null) {
        context.getSnapshotReplication().close();
      }
    } catch (final Exception e) {
      Loggers.SYSTEM_LOGGER.error(
          "Unexpected error closing state replication for partition {}",
          context.getPartitionId(),
          e);
    } finally {
      context.setSnapshotReplication(null);
    }

    return CompletableFuture.completedFuture(context);
  }

  private boolean shouldReplicateSnapshots(final PartitionStartupContext state) {
    return state.getBrokerCfg().getCluster().getReplicationFactor() > 1;
  }
}
