/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

public final class RaftBootstrapStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Bootstrapped Raft Partition";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var partition =
        context
            .raftPartitionFactory()
            .createRaftPartition(context.partitionMetadata(), context.partitionDirectory());

    final var open = partition.open(context.partitionManagementService(), context.snapshotStore());
    open.whenComplete(
        (raftPartition, throwable) -> {
          if (throwable == null) {
            context.raftPartition(raftPartition);
            result.complete(context);
          } else {
            result.completeExceptionally(throwable);
          }
        });

    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var close = context.raftPartition().close();
    close.whenComplete(
        (ignored, throwable) -> {
          if (throwable == null) {
            result.complete(context.raftPartition(null));
          } else {
            result.completeExceptionally(throwable);
          }
        });

    return result;
  }
}
