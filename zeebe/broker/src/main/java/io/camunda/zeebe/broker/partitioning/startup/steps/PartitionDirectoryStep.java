/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory.GROUP_NAME;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionDirectoryStep implements StartupStep<PartitionStartupContext> {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionDirectoryStep.class);

  @Override
  public String getName() {
    return "Partition Directory";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    final var dataDirectory = Paths.get(context.brokerConfig().getData().getDirectory());
    final var partitionId = context.partitionId();
    final var partitionDirectory =
        dataDirectory.resolve(GROUP_NAME).resolve("partitions").resolve(partitionId.toString());
    final var temporaryPartitionDirectory =
        dataDirectory
            .resolve(GROUP_NAME)
            .resolve("temporary-partitions")
            .resolve(partitionId.toString());
    final var directories = List.of(partitionDirectory, temporaryPartitionDirectory);

    for (final Path path : directories) {
      try {
        FileUtil.ensureDirectoryExists(path);
        FileUtil.flushDirectory(path);
      } catch (final IOException e) {
        result.completeExceptionally(e);
      }
    }
    result.complete(
        context
            .partitionDirectory(partitionDirectory)
            .temporaryPartitionDirectory(temporaryPartitionDirectory));
    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var temporaryDirectory = context.temporaryPartitionDirectory();
    if (temporaryDirectory != null) {
      try {
        FileUtil.deleteFolder(temporaryDirectory);
        FileUtil.flushDirectory(temporaryDirectory.getParent());
      } catch (final IOException e) {
        LOG.warn("Unable to delete temporary partition directory " + temporaryDirectory + " .", e);
      }
    }
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();
    result.complete(context.partitionDirectory(null).temporaryPartitionDirectory(null));
    return result;
  }
}
