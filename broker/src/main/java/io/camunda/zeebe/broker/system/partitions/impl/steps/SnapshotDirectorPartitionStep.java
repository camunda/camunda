/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContextImpl;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;

public class SnapshotDirectorPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionTransitionContextImpl context) {
    final var server = context.getRaftPartition().getServer();

    final Duration snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
    final var director =
        new AsyncSnapshotDirector(
            context.getNodeId(),
            context.getPartitionId(),
            context.getStreamProcessor(),
            context.getSnapshotController(),
            snapshotPeriod);

    server.addCommittedEntryListener(director);

    context.setSnapshotDirector(director);
    context.getComponentHealthMonitor().registerComponent(director.getName(), director);

    return context.getActorSchedulingService().submitActor(director);
  }

  @Override
  public ActorFuture<Void> close(final PartitionTransitionContextImpl context) {
    final var director = context.getSnapshotDirector();
    context.getComponentHealthMonitor().removeComponent(director.getName());
    final ActorFuture<Void> future = director.closeAsync();
    context.setSnapshotDirector(null);
    return future;
  }

  @Override
  public String getName() {
    return "AsyncSnapshotDirector";
  }
}
