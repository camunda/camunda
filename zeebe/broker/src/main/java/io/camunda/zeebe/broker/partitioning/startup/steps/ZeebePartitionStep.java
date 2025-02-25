/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

public final class ZeebePartitionStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Zeebe Partition";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var zeebePartition =
        context
            .zeebePartitionFactory()
            .constructPartition(
                context.raftPartition(),
                context.snapshotStore(),
                context.initialPartitionConfig(),
                context.brokerHealthCheckService(),
                context.partitionMeterRegistry());
    final var submit = context.schedulingService().submitActor(zeebePartition);
    context
        .concurrencyControl()
        .runOnCompletion(
            submit,
            (ignored, failure) -> {
              if (failure == null) {
                result.complete(context.zeebePartition(zeebePartition));
              } else {
                result.completeExceptionally(failure);
              }
            });

    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext partitionStartupContext) {
    final var result =
        partitionStartupContext.concurrencyControl().<PartitionStartupContext>createFuture();

    final var zeebePartition = partitionStartupContext.zeebePartition();
    if (zeebePartition == null) {
      result.complete(partitionStartupContext);
      return result;
    }

    final var close = zeebePartition.closeAsync();
    partitionStartupContext
        .concurrencyControl()
        .runOnCompletion(
            close,
            (ignored, failure) -> {
              if (failure == null) {
                result.complete(partitionStartupContext.zeebePartition(null));
              } else {
                result.completeExceptionally(failure);
              }
            });
    return result;
  }
}
