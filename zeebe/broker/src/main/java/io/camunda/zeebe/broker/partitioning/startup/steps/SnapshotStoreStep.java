/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotTransferServiceClient;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.db.impl.rocksdb.ChecksumProviderRocksDBImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import io.camunda.zeebe.util.VisibleForTesting;

public class SnapshotStoreStep implements StartupStep<PartitionStartupContext> {

  @Override
  public String getName() {
    return "Snapshot Store";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            context.brokerConfig().getCluster().getNodeId(),
            context.partitionId(),
            context.partitionDirectory(),
            new ChecksumProviderRocksDBImpl(),
            context.partitionMeterRegistry());

    var result =
        context
            .schedulingService()
            .submitActor(snapshotStore, SchedulingHints.ioBound())
            .thenApply(v -> context.snapshotStore(snapshotStore), context.concurrencyControl());
    if (context.isInitializeFromSnapshot()) {
      result =
          result.andThen(
              ctx -> initializeFromSnapshot(ctx, snapshotStore), context.concurrencyControl());
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

  ActorFuture<PartitionStartupContext> initializeFromSnapshot(
      final PartitionStartupContext context, final FileBasedSnapshotStore snapshotStore) {
    return receiveSnapshot(
            context.partitionId(),
            snapshotStore,
            context.brokerClient(),
            context.concurrencyControl())
        .andThen(
            snapshot -> snapshotStore.restore(snapshot).thenApply(ignored -> context),
            context.concurrencyControl());
  }

  @VisibleForTesting
  ActorFuture<PersistedSnapshot> receiveSnapshot(
      final int partitionId,
      final FileBasedSnapshotStore snapshotStore,
      final BrokerClient brokerClient,
      final ConcurrencyControl concurrency) {
    final var transfer =
        new SnapshotTransfer(
            new SnapshotTransferServiceClient(brokerClient), snapshotStore, concurrency);
    return transfer.getLatestSnapshot(partitionId);
  }
}
