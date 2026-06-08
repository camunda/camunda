/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A limited {@link PartitionManager} used when the cluster is in recovery mode. Only a restricted
 * set of resources is available.
 *
 * <p>Each local partition is started through a sequence of {@link
 * io.camunda.zeebe.scheduler.startup.StartupStep}s encapsulated in {@link RecoveryPartition},
 * mirroring the normal {@link Partition} bootstrapping approach.
 */
public final class RecoveryPartitionManager extends AbstractPartitionManager {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryPartitionManager.class);
  private final List<RecoveryPartition> recoveryPartitions = new ArrayList<>();
  private final String dataDirectory;
  private final MeterRegistry meterRegistry;

  public RecoveryPartitionManager(
      final String partitionGroup,
      final String dataDirectory,
      final ConcurrencyControl concurrencyControl,
      final ClusterConfigurationService clusterConfigurationService,
      final ClusterServices clusterServices,
      final ActorSchedulingService schedulingService,
      final BrokerInfo brokerInfo,
      final MeterRegistry meterRegistry) {
    super(
        partitionGroup,
        concurrencyControl,
        schedulingService,
        clusterConfigurationService,
        clusterServices.getMembershipService(),
        brokerInfo);
    this.dataDirectory = dataDirectory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public RaftPartition getRaftPartition(final int partitionId) {
    return null;
  }

  @Override
  public Collection<RaftPartition> getRaftPartitions() {
    return List.of();
  }

  @Override
  public Collection<ZeebePartition> getZeebePartitions() {
    return Collections.emptyList();
  }

  @Override
  public ActorFuture<Void> stop() {
    LOG.info("Stopping RecoveryPartitionManager");
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(() -> stopInternal(result));
    return result;
  }

  public ActorFuture<Void> start() {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(() -> startInternal(result));
    return result;
  }

  private void startInternal(final ActorFuture<Void> result) {
    final var localPartitions = localPartitions();
    if (localPartitions.isEmpty()) {
      result.complete(null);
      return;
    }

    submitTopologyManager();
    final var startFutures = startPartitions(localPartitions);

    concurrencyControl.runOnCompletion(
        startFutures,
        startError -> {
          if (startError != null) {
            result.completeExceptionally(startError);
            return;
          }
          final var topologyUpdateFutures = deactivatePartitions(localPartitions);
          concurrencyControl.runOnCompletion(
              topologyUpdateFutures,
              topologyError -> {
                if (topologyError != null) {
                  result.completeExceptionally(topologyError);
                } else {
                  result.complete(null);
                }
              });
        });
  }

  private void stopInternal(final ActorFuture<Void> result) {
    final var stopFutures =
        recoveryPartitions.stream()
            .map(RecoveryPartition::stop)
            .collect(new ActorFutureCollector<>(concurrencyControl));

    concurrencyControl.runOnCompletion(
        stopFutures,
        (ignored, stopError) -> {
          recoveryPartitions.clear();
          if (stopError != null) {
            LOG.error("Failed to stop recovery partitions", stopError);
            result.completeExceptionally(stopError);
          } else {
            closeTopologyManager().onComplete(result);
          }
        });
  }

  private List<ActorFuture<RecoveryPartition>> startPartitions(
      final List<PartitionMetadata> localPartitions) {
    final List<ActorFuture<RecoveryPartition>> startFutures = new ArrayList<>();
    localPartitions.forEach(
        m -> {
          final var partitionId = m.id();
          final var partitionDir = partitionDirectory(partitionId);
          final var context =
              new RecoveryPartitionStartupContext(
                  partitionId,
                  partitionDir,
                  actorSchedulingService,
                  topologyManager,
                  meterRegistry,
                  concurrencyControl);

          final var partition = RecoveryPartition.recovering(context);
          recoveryPartitions.add(partition);
          startFutures.add(partition.start());
        });
    return startFutures;
  }

  private List<ActorFuture<Void>> deactivatePartitions(
      final List<PartitionMetadata> localPartitions) {
    return localPartitions.stream().map(m -> topologyManager.setInactive(m.id().id())).toList();
  }

  private Path partitionDirectory(final PartitionId partitionId) {
    return RaftPartitionFactory.getPartitionDirectory(partitionId, dataDirectory);
  }
}
