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
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Paths;

public final class PartitionDirectoryStep implements StartupStep<PartitionStartupContext> {

  private final String name;

  public PartitionDirectoryStep(final int partitionId) {
    name = String.format("Partition %d - Directory", partitionId);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    final var dataDirectory = Paths.get(context.brokerConfig().getData().getDirectory());
    final var partitionId = context.partitionMetadata().id().id();
    final var partitionDirectory =
        dataDirectory
            .resolve(context.getPartitionGroup())
            .resolve("partitions")
            .resolve(partitionId.toString());

    try {
      FileUtil.ensureDirectoryExists(partitionDirectory);
      result.complete(context.partitionDirectory(partitionDirectory));
    } catch (final IOException e) {
      result.completeExceptionally(e);
    }
    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    result.complete(context.partitionDirectory(null));
    return result;
  }
}
