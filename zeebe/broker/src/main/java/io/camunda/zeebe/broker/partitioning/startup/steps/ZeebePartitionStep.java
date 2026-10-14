/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

public final class ZeebePartitionStep implements StartupStep<PartitionStartupContext> {

  private final String name;

  public ZeebePartitionStep(final int partitionId) {
    name = String.format("Partition %d - Zeebe Partition", partitionId);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var factory = context.zeebePartitionFactory();
    final var loadCounters = factory.loadCounters().register();
    final ZeebePartition zeebePartition;
    try {
      zeebePartition =
          factory.constructPartition(
              context.raftPartition(),
              context.snapshotStore(),
              context.initialPartitionConfig(),
              context.brokerHealthCheckService(),
              context.partitionMeterRegistry(),
              context.commandApiService(),
              loadCounters);
    } catch (final Exception e) {
      factory.loadCounters().retire(loadCounters);
      result.completeExceptionally(e);
      return result;
    }
    final var submit = context.schedulingService().submitActor(zeebePartition);
    context
        .concurrencyControl()
        .runOnCompletion(
            submit,
            (ignored, failure) -> {
              if (failure == null) {
                result.complete(context.zeebePartition(zeebePartition).loadCounters(loadCounters));
              } else {
                factory.loadCounters().retire(loadCounters);
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
                final var loadCounters = partitionStartupContext.loadCounters();
                if (loadCounters != null) {
                  partitionStartupContext
                      .zeebePartitionFactory()
                      .loadCounters()
                      .retire(loadCounters);
                }
                result.complete(partitionStartupContext.zeebePartition(null).loadCounters(null));
              } else {
                result.completeExceptionally(failure);
              }
            });
    return result;
  }
}
