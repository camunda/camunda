/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;

public final class SnapshotStoreStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Snapshot Store";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var snapshotStore =
        new FileBasedSnapshotStore(
            context.brokerConfig().getCluster().getNodeId(),
            context.partitionMetadata().id().id(),
            context.partitionDirectory(),
            new ChecksumProviderRocksDBImpl(),
            context.partitionMeterRegistry());

    final var submit =
        context.schedulingService().submitActor(snapshotStore, SchedulingHints.ioBound());
    context
        .concurrencyControl()
        .runOnCompletion(
            submit,
            (ignored, failure) -> {
              if (failure == null) {
                context.snapshotStore(snapshotStore);
                result.complete(context);
              } else {
                result.completeExceptionally(failure);
              }
            });

    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var result = context.concurrencyControl().<PartitionStartupContext>createFuture();

    final var snapshotStore = context.snapshotStore();
    if (snapshotStore == null) {
      result.complete(context);
      return result;
    }

    final var close = snapshotStore.closeAsync();
    context
        .concurrencyControl()
        .runOnCompletion(
            close,
            (ignored, failure) -> {
              if (failure == null) {
                result.complete(context.snapshotStore(null));
              } else {
                result.completeExceptionally(failure);
              }
            });

    return result;
  }
}
