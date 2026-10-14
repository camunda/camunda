/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.raft.ZeebeEntryValidator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.syspartition.detector.FailureDetectorFactory;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.micrometer.MicrometerUtil;
import io.camunda.zeebe.util.micrometer.PartitionKeyNames;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.InstantSource;

/** Creates the Raft partition and snapshot store backing the {@link SystemPartition}. */
public final class SystemPartitionFactory {
  private final BrokerCfg brokerCfg;
  private final MeterRegistry brokerMeterRegistry;
  private final ClusterCommunicationService communicationService;
  private final MemberId localMemberId;

  public SystemPartitionFactory(
      final BrokerCfg brokerCfg,
      final MeterRegistry brokerMeterRegistry,
      final ClusterCommunicationService communicationService,
      final MemberId localMemberId) {
    this.brokerCfg = brokerCfg;
    this.brokerMeterRegistry = brokerMeterRegistry;
    this.communicationService = communicationService;
    this.localMemberId = localMemberId;
  }

  public ActorFuture<SystemPartition> create(final ActorSchedulingService scheduler) {
    final var partitionId = SystemPartition.PARTITION_ID;
    final var directory =
        RaftPartitionFactory.getPartitionDirectory(partitionId, brokerCfg.getData().getDirectory());
    try {
      FileUtil.ensureDirectoryExists(directory);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    final var meterRegistry =
        MicrometerUtil.wrap(brokerMeterRegistry, PartitionKeyNames.tags(partitionId));
    final var snapshotStore =
        new FileBasedSnapshotStore(
            brokerCfg.getCluster().getNodeId(),
            partitionId,
            directory,
            SystemPartitionStateController.FILE_INFO_PROVIDER,
            meterRegistry);
    // Entries carry contiguous positions like those of data partitions, which the validator checks.
    final var raftPartition =
        new RaftPartitionFactory(brokerCfg)
            .createRaftPartition(
                SystemPartition.metadata(brokerCfg),
                directory,
                meterRegistry,
                new ZeebeEntryValidator(),
                brokerCfg.getCluster().getSystemPartition().getLogSegmentSize().toBytes());

    return scheduler
        .submitActor(snapshotStore, SchedulingHints.ioBound())
        .thenApply(
            ok -> {
              final var systemPartition =
                  new SystemPartition(
                      raftPartition,
                      snapshotStore,
                      meterRegistry,
                      brokerCfg.getCluster().getSystemPartition().getSnapshotPeriod());
              systemPartition.addListener(
                  new ClusterIdentityInitializer(
                      systemPartition.keyValueReader(),
                      brokerCfg.getCluster().getClusterId(),
                      InstantSource.system()));
              FailureDetectorFactory.install(
                  brokerCfg.getCluster().getSystemPartition().getFailureDetector(),
                  systemPartition,
                  communicationService,
                  localMemberId,
                  SystemPartition.members(brokerCfg),
                  meterRegistry);
              return systemPartition;
            });
  }

  public static ActorFuture<Void> close(
      final SystemPartition systemPartition, final ConcurrencyControl concurrencyControl) {
    final ActorFuture<Void> result = concurrencyControl.createFuture();
    final var actors = systemPartition.actors().stream().map(Actor::closeAsync).toList();
    if (actors.isEmpty()) {
      closeRaftAndStore(systemPartition, concurrencyControl, result);
    } else {
      concurrencyControl.runOnCompletion(
          actors, error -> closeRaftAndStore(systemPartition, concurrencyControl, result));
    }
    return result;
  }

  private static void closeRaftAndStore(
      final SystemPartition systemPartition,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<Void> result) {
    systemPartition
        .raftPartition()
        .close()
        .whenComplete(
            // completes on the Raft thread; hop back to the caller's actor to continue
            (ok, raftError) ->
                concurrencyControl.run(
                    () ->
                        concurrencyControl.runOnCompletion(
                            systemPartition.snapshotStore().closeAsync(),
                            (closed, storeError) -> {
                              MicrometerUtil.close(systemPartition.meterRegistry());
                              final var error = raftError != null ? raftError : storeError;
                              if (error != null) {
                                result.completeExceptionally(error);
                              } else {
                                result.complete(null);
                              }
                            })));
  }
}
