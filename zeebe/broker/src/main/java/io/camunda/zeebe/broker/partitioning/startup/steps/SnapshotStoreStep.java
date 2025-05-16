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
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;

public final class SnapshotStoreStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Snapshot Store";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            context.brokerConfig().getCluster().getNodeId(),
            context.partitionMetadata().id().id(),
            context.partitionDirectory(),
            new ChecksumProviderRocksDBImpl(),
            context.partitionMeterRegistry());

    var result =
        context
            .schedulingService()
            .submitActor(snapshotStore, SchedulingHints.ioBound())
            .thenApply(v -> context.snapshotStore(snapshotStore), context.concurrencyControl());
    if (context.isInitializeFromSnapshot()) {
      // TODO acquire the snapshot and initialize it using it
      result = result.thenApply(ignored -> context);
    }
    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    final var snapshotStore = context.snapshotStore();
    if (snapshotStore == null) {
      return CompletableActorFuture.completed(context);
    } else {
      return snapshotStore
          .closeAsync()
          .thenApply(ignored -> context.snapshotStore(null), context.concurrencyControl());
    }
  }
}
