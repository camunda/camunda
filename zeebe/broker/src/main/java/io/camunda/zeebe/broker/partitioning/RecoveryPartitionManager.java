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
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
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
 * set of resources is available: data directory access, cluster topology, cluster services, and the
 * backup store. No Raft partitions or Zeebe partitions are started.
 *
 * <p>Each local partition is started through a sequence of {@link
 * io.camunda.zeebe.scheduler.startup.StartupStep}s encapsulated in {@link RecoveryPartition},
 * mirroring the normal {Partition} bootstrapping approach.
 */
public final class RecoveryPartitionManager extends Actor implements PartitionManager {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryPartitionManager.class);
  private final List<RecoveryPartition> recoveryPartitions = new ArrayList<>();
  private final String dataDirectory;
  private final String partitionGroup;
  private final MeterRegistry meterRegistry;
  private final ClusterConfigurationService clusterConfigurationService;
  private final ClusterServices clusterServices;
  private final ActorSchedulingService schedulingService;
  private final TopologyManagerImpl topologyManager;

  public RecoveryPartitionManager(
      final String partitionGroup,
      final String dataDirectory,
      final ClusterConfigurationService clusterConfigurationService,
      final ClusterServices clusterServices,
      final ActorSchedulingService schedulingService,
      final BrokerInfo brokerInfo,
      final MeterRegistry meterRegistry) {
    this.dataDirectory = dataDirectory;
    this.partitionGroup = partitionGroup;
    this.clusterConfigurationService = clusterConfigurationService;
    this.clusterServices = clusterServices;
    this.schedulingService = schedulingService;
    this.meterRegistry = meterRegistry;
    topologyManager = new TopologyManagerImpl(clusterServices.getMembershipService(), brokerInfo);
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
    final var result = new CompletableActorFuture<Void>();
    actor.run(() -> stopInternal(result));
    return result;
  }

  private void stopInternal(final CompletableActorFuture<Void> result) {
    final var stopFutures = recoveryPartitions.stream().map(RecoveryPartition::stop).toList();

    actor.runOnCompletion(
        stopFutures,
        stopError -> {
          recoveryPartitions.clear();
          topologyManager
              .closeAsync()
              .onComplete(
                  (ok, closeErr) -> {
                    // Complete result before closing the actor — the close future's
                    // onComplete
                    // callback would never fire because it expects to run on the actor's
                    // thread,
                    // but the actor is already being closed at that point (self-close
                    // deadlock).
                    if (stopError != null) {
                      result.completeExceptionally(stopError);
                    } else {
                      result.complete(null);
                    }
                    actor.close();
                  });
        });
  }

  public ActorFuture<Void> start() {
    final var result = new CompletableActorFuture<Void>();
    actor.run(() -> startInternal(result));
    return result;
  }

  private void startInternal(final CompletableActorFuture<Void> result) {
    final var partitionDistribution =
        clusterConfigurationService.getPartitionDistribution().withGroupName(partitionGroup);
    final var localMemberId = clusterServices.getMembershipService().getLocalMember().id();

    final var localPartitions =
        partitionDistribution.partitions().stream()
            .filter(m -> m.members().contains(localMemberId))
            .toList();

    if (localPartitions.isEmpty()) {
      result.complete(null);
      return;
    }

    schedulingService.submitActor(topologyManager);
    final var startFutures = startPartitions(localPartitions);

    actor.runOnCompletion(
        startFutures,
        startError -> {
          if (startError != null) {
            result.completeExceptionally(startError);
            return;
          }
          final var topologyUpdateFutures = deactivatePartitions(localPartitions);
          actor.runOnCompletion(
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
                  schedulingService,
                  topologyManager,
                  meterRegistry,
                  actor);

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
