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
import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.List;

public class LogDeletionPartitionStartupStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "LogDeletionService";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(
      final PartitionStartupContext partitionStartupContext) {
    final LogCompactor logCompactor =
        new AtomixLogCompactor(partitionStartupContext.getRaftPartition().getServer());
    final LogDeletionService deletionService =
        new LogDeletionService(
            partitionStartupContext.getNodeId(),
            partitionStartupContext.getPartitionId(),
            logCompactor,
            List.of(
                partitionStartupContext.getConstructableSnapshotStore(),
                partitionStartupContext.getReceivableSnapshotStore()));

    partitionStartupContext.setLogDeletionService(deletionService);
    final ActorFuture<PartitionStartupContext> startupFuture = new CompletableActorFuture<>();
    partitionStartupContext
        .getActorSchedulingService()
        .submitActor(deletionService)
        .onComplete(
            (success, failure) -> {
              if (failure != null) {
                startupFuture.completeExceptionally(failure);
              } else {
                startupFuture.complete(partitionStartupContext);
              }
            });
    return startupFuture;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext partitionStartupContext) {
    final ActorFuture<Void> closeFuture =
        partitionStartupContext.getLogDeletionService().closeAsync();
    partitionStartupContext.setLogDeletionService(null);
    final ActorFuture<PartitionStartupContext> shutdownFuture = new CompletableActorFuture<>();
    closeFuture.onComplete(
        (success, failure) -> {
          if (failure != null) {
            shutdownFuture.completeExceptionally(failure);
          } else {
            shutdownFuture.complete(partitionStartupContext);
          }
        });
    return shutdownFuture;
  }
}
