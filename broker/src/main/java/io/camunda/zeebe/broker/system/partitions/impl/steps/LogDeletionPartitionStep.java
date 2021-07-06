/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.logstreams.AtomixLogCompactor;
import io.camunda.zeebe.broker.logstreams.LogCompactor;
import io.camunda.zeebe.broker.logstreams.LogDeletionService;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContextImpl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.List;

public class LogDeletionPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionTransitionContextImpl context) {
    final LogCompactor logCompactor =
        new AtomixLogCompactor(context.getRaftPartition().getServer());
    final LogDeletionService deletionService =
        new LogDeletionService(
            context.getNodeId(),
            context.getPartitionId(),
            logCompactor,
            List.of(context.getConstructableSnapshotStore(), context.getReceivableSnapshotStore()));

    context.setLogDeletionService(deletionService);
    return context.getActorSchedulingService().submitActor(deletionService);
  }

  @Override
  public ActorFuture<Void> close(final PartitionTransitionContextImpl context) {
    final ActorFuture<Void> future = context.getLogDeletionService().closeAsync();
    context.setLogDeletionService(null);
    return future;
  }

  @Override
  public String getName() {
    return "LogDeletionService";
  }
}
