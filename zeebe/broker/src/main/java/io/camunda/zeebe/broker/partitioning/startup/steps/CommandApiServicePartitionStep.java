/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class CommandApiServicePartitionStep implements StartupStep<PartitionStartupContext> {

  private final String name;

  public CommandApiServicePartitionStep(final int partitionId) {
    name = String.format("Partition %d - Command API Service", partitionId);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var commandApiService =
        new CommandApiServiceImpl(
            context.partitionMetadata().id(),
            context.gatewayBrokerTransport(),
            context.schedulingService(),
            context.brokerConfig().getExperimental().getQueryApi());

    context
        .concurrencyControl()
        .runOnCompletion(
            context.schedulingService().submitActor(commandApiService),
            (ignored, error) -> {
              if (error != null) {
                commandApiService.closeAsync();
                result.completeExceptionally(error);
                return;
              }
              try {
                context.diskSpaceUsageMonitor().addDiskUsageListener(commandApiService);
                context.commandApiService(commandApiService);
                result.complete(context);
              } catch (final Exception e) {
                commandApiService.closeAsync();
                result.completeExceptionally(e);
              }
            });

    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    final var commandApiService = context.commandApiService();
    if (commandApiService == null) {
      result.complete(context);
      return result;
    }

    context.diskSpaceUsageMonitor().removeDiskUsageListener(commandApiService);
    context
        .concurrencyControl()
        .runOnCompletion(
            commandApiService.closeAsync(),
            (ignored, failure) -> {
              context.commandApiService(null);
              if (failure != null) {
                result.completeExceptionally(failure);
              } else {
                result.complete(context);
              }
            });
    return result;
  }
}
