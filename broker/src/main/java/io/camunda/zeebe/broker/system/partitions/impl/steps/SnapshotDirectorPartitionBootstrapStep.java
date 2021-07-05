/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapContext;
import io.camunda.zeebe.broker.system.partitions.PartitionBootstrapStep;
import io.camunda.zeebe.broker.system.partitions.impl.AsyncSnapshotDirector;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;

public class SnapshotDirectorPartitionBootstrapStep implements PartitionBootstrapStep {

  @Override
  public ActorFuture<PartitionBootstrapContext> open(final PartitionBootstrapContext context) {
    final Duration snapshotPeriod = context.getBrokerCfg().getData().getSnapshotPeriod();
    final AsyncSnapshotDirector director =
        new AsyncSnapshotDirector(
            context.getNodeId(),
            context.getStreamProcessor(),
            context.getStateController(),
            context.getLogStream(),
            snapshotPeriod);

    final var future = context.getActorSchedulingService().submitActor(director);

    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    future.onComplete(
        (success, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
          } else {
            context.setSnapshotDirector(director);
            context.getComponentHealthMonitor().registerComponent(director.getName(), director);
            result.complete(context);
          }
        });
    return result;
  }

  @Override
  public ActorFuture<PartitionBootstrapContext> close(final PartitionBootstrapContext context) {
    final var director = context.getSnapshotDirector();

    final var result = new CompletableActorFuture<PartitionBootstrapContext>();

    final ActorFuture<Void> future = director.closeAsync();

    future.onComplete(
        (success, error) -> {
          if (error != null) {

            result.completeExceptionally(error);
          } else {
            context.getComponentHealthMonitor().removeComponent(director.getName());
            context.setSnapshotDirector(null);
            result.complete(context);
          }
        });

    return result;
  }

  @Override
  public String getName() {
    return "AsyncSnapshotDirector";
  }
}
