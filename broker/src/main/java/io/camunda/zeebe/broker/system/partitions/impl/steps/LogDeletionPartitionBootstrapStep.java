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
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.List;

public class LogDeletionPartitionBootstrapStep implements PartitionBootstrapStep {

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    final LogCompactor logCompactor =
        new AtomixLogCompactor(context.getRaftPartition().getServer());
    final LogDeletionService deletionService =
        new LogDeletionService(
            context.getNodeId(),
            context.getPartitionId(),
            logCompactor,
            List.of(context.getConstructableSnapshotStore(), context.getReceivableSnapshotStore()));

    context.setLogDeletionService(deletionService);
    final var deletionServiceFuture =
        context.getActorSchedulingService().submitActor(deletionService);

    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    deletionServiceFuture.onComplete(
        (success, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            result.complete(context);
          }
        });
    return result;
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {

    final ActorFuture<Void> future = context.getLogDeletionService().closeAsync();

    final var result = new CompletableActorFuture<PartitionBootstrapContext>();
    future.onComplete(
        (success, error) -> {
          if (error != null) {
            future.completeExceptionally(error);
          } else {
            context.setLogDeletionService(null);
            result.complete(context);
          }
        });
    return result;
  }

  @Override
  public String getName() {
    return "LogDeletionService";
  }
}
