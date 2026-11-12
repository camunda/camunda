/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static io.camunda.zeebe.scheduler.AsyncClosable.closeHelper;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotTransferServiceClient;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.partitioning.startup.SnapshotInitializationUtil;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg.PrimaryStorageType;
import io.camunda.zeebe.db.impl.rocksdb.RocksDBSnapshotFileInfoProvider;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.snapshots.SnapshotFileInfoProvider;
import io.camunda.zeebe.snapshots.SnapshotFilesInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferImpl;

public class SnapshotStoreStep implements StartupStep<PartitionStartupContext> {

  private final String name;

  public SnapshotStoreStep(final int partitionId) {
    name = String.format("Partition %d - Snapshot Store", partitionId);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(final PartitionStartupContext context) {
    final var snapshotStore =
        new FileBasedSnapshotStore(
            context.brokerConfig().getCluster().getNodeId(),
            context.partitionMetadata().id(),
            context.partitionDirectory(),
            snapshotFileInfoProvider(context.brokerConfig()),
            context.partitionMeterRegistry());

    var result =
        context
            .schedulingService()
            .submitActor(snapshotStore, SchedulingHints.ioBound())
            .thenApply(v -> context.snapshotStore(snapshotStore), context.concurrencyControl());

    if (context.isInitializeFromSnapshot()) {
      result =
          result
              .andThen(
                  ignored -> {
                    final var snapshotTransfer =
                        new SnapshotTransferImpl(
                            context.partitionMetadata().id(),
                            actor ->
                                new SnapshotTransferServiceClient(
                                    context.brokerClient(),
                                    context.partitionMetadata().id().group(),
                                    context
                                        .brokerConfig()
                                        .getExperimental()
                                        .getRaft()
                                        .getBootstrapSnapshotRequestTimeout()),
                            snapshotStore.getSnapshotMetrics(),
                            snapshotStore);
                    return context
                        .schedulingService()
                        .submitActor(snapshotTransfer, SchedulingHints.IO_BOUND)
                        .thenApply(
                            empty -> {
                              context.setSnapshotTransfer(snapshotTransfer);
                              return context;
                            },
                            context.concurrencyControl());
                  },
                  context.concurrencyControl())
              .andThen(
                  ctx ->
                      SnapshotInitializationUtil.initializeFromSnapshot(
                              snapshotStore,
                              context.snapshotTransfer(),
                              context.concurrencyControl())
                          .thenApply(ignored -> ctx, context.concurrencyControl()),
                  context.concurrencyControl());
    }
    return result;
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(final PartitionStartupContext context) {
    return closeHelper(context.snapshotTransfer())
        .andThen(ignore -> closeHelper(context.snapshotStore()), context.concurrencyControl())
        .thenApply(ignored -> context.snapshotStore(null), context.concurrencyControl());
  }

  /**
   * Snapshots of the relational storage hold only a small marker file, not RocksDB files, so their
   * checksums and sizes come from reading the files themselves.
   */
  private static SnapshotFileInfoProvider snapshotFileInfoProvider(final BrokerCfg brokerCfg) {
    if (brokerCfg.getData().getPrimaryStorageType() == PrimaryStorageType.RDBMS) {
      return snapshotPath -> SnapshotFilesInfo.none();
    }
    return new RocksDBSnapshotFileInfoProvider();
  }
}
