/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.SnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.NoneSnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.StateReplication;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class SnapshotReplicationPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final SnapshotReplication replication =
        shouldReplicateSnapshots(context)
            ? new StateReplication(
                context.getMessagingService(), context.getPartitionId(), context.getNodeId())
            : new NoneSnapshotReplication();

    context.setSnapshotReplication(replication);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
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

    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "SnapshotReplication";
  }

  private boolean shouldReplicateSnapshots(final PartitionContext state) {
    return state.getBrokerCfg().getCluster().getReplicationFactor() > 1;
  }
}
