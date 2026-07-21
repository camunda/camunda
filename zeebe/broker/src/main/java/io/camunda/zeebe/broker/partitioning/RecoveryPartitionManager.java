/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.restore.validation.RestoreValidator;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A limited {@link RecoveryPartitionManager} used when the cluster is in recovery mode. Only a
 * restricted set of resources is available.
 *
 * <p>Each local partition is started through a sequence of {@link
 * io.camunda.zeebe.scheduler.startup.StartupStep}s encapsulated in {@link RecoveryPartition},
 * mirroring the normal {@link Partition} bootstrapping approach.
 */
public final class RecoveryPartitionManager
    implements PartitionManager, PartitionChangeExecutor, PartitionScalingChangeExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryPartitionManager.class);
  private final List<RecoveryPartition> recoveryPartitions = new ArrayList<>();
  private final List<Integer> failedPartitionIds = new ArrayList<>();
  private final String partitionGroup;
  private final ConcurrencyControl concurrencyControl;
  private final ActorSchedulingService actorSchedulingService;
  private final ClusterConfigurationService clusterConfigurationService;
  private final ClusterMembershipService membershipService;
  private final TopologyManagerImpl topologyManager;
  private final MeterRegistry meterRegistry;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo brokerInfo;
  private final AtomixServerTransport gatewayBrokerTransport;
  private final @Nullable IntFunction<Long> exportedPositionSupplier;
  private @Nullable BackupStore backupStore;

  public RecoveryPartitionManager(
      final String partitionGroup,
      final BrokerCfg brokerCfg,
      final BrokerInfo brokerInfo,
      final ConcurrencyControl concurrencyControl,
      final ClusterConfigurationService clusterConfigurationService,
      final ClusterMembershipService membershipService,
      final ActorSchedulingService schedulingService,
      final MeterRegistry meterRegistry,
      final AtomixServerTransport gatewayBrokerTransport,
      final @Nullable IntFunction<Long> exportedPositionSupplier,
      final TopologyManagerImpl topologyManager) {
    this.partitionGroup = partitionGroup;
    this.concurrencyControl = concurrencyControl;
    actorSchedulingService = schedulingService;
    this.clusterConfigurationService = clusterConfigurationService;
    this.topologyManager = topologyManager;
    this.meterRegistry = meterRegistry;
    this.membershipService = membershipService;
    this.brokerCfg = brokerCfg;
    this.brokerInfo = brokerInfo;
    this.gatewayBrokerTransport = gatewayBrokerTransport;
    this.exportedPositionSupplier = exportedPositionSupplier;
  }

  @Override
  public @Nullable RaftPartition getRaftPartition(final int partitionId) {
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
  public ActorFuture<Void> start() {
    LOG.info("Recovering partitions for partition group {}", partitionGroup);
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (DEFAULT_GROUP_NAME.equals(partitionGroup)) {
            clusterConfigurationService.registerPartitionChangeExecutors(this, this);
          }
          startInternal(result);
        });
    return result;
  }

  @Override
  public ActorFuture<Void> stop() {
    LOG.info("Stopping RecoveryPartitionManager");
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          if (DEFAULT_GROUP_NAME.equals(partitionGroup)) {
            clusterConfigurationService.removePartitionChangeExecutor();
          }
          clusterConfigurationService.removeRequestValidator(partitionGroup, RestoreRequest.class);
          stopInternal(result);
        });
    return result;
  }

  private void startInternal(final ActorFuture<Void> result) {
    final var localPartitions = localPartitions();
    if (localPartitions.isEmpty()) {
      LOG.info("No local partitions to recover for partition group {}", partitionGroup);
      result.complete(null);
      return;
    }

    final var backupCfg = brokerCfg.getData().getBackup();
    try {
      backupStore = BackupCfg.BackupStoreFactory.createStore(backupCfg);
    } catch (final Exception e) {
      LOG.error("Failed to create backup store for partition group {}", partitionGroup, e);
      result.completeExceptionally(e);
      return;
    }

    final var partitionCount =
        clusterConfigurationService.getCurrentClusterConfiguration().partitionCount();
    clusterConfigurationService.registerRequestValidator(
        partitionGroup,
        new RestoreValidator(partitionCount, backupStore, exportedPositionSupplier));

    final var startFutures = new ArrayList<ActorFuture<Void>>();
    for (final var partitionMetadata : localPartitions) {
      LOG.info("Recovering partition {}", partitionMetadata.id());
      final var partition = RecoveryPartition.recovering(startupContext(partitionMetadata));
      startFutures.add(startPartition(partition));
    }

    final var startAll =
        startFutures.stream().collect(new ActorFutureCollector<>(concurrencyControl));
    concurrencyControl.runOnCompletion(
        startAll,
        (ignored, startError) -> {
          if (startError != null) {
            LOG.warn(
                "Recovered {}/{} partitions for partition group {}",
                recoveryPartitions.size(),
                localPartitions.size(),
                partitionGroup);
          }
          final var deactivateFutures =
              localPartitions.stream()
                  .map(p -> topologyManager.setInactive(p.id().number()))
                  .collect(new ActorFutureCollector<>(concurrencyControl));
          concurrencyControl.runOnCompletion(
              deactivateFutures,
              (ignoredDeactivate, deactivateError) -> {
                // reported once the partition is registered as INACTIVE above, since
                // onHealthChanged is a no-op for a partition the topology doesn't know about yet
                recoveryPartitions.forEach(
                    p ->
                        topologyManager.onHealthChanged(
                            p.partitionId().number(), HealthStatus.HEALTHY));
                failedPartitionIds.forEach(
                    id -> topologyManager.onHealthChanged(id, HealthStatus.DEAD));
                if (recoveryPartitions.isEmpty()) {
                  if (deactivateError != null) {
                    LOG.error(
                        "Failed to deactivate local partitions for partition group {} after all"
                            + " partitions failed to recover",
                        partitionGroup,
                        deactivateError);
                  }
                  concurrencyControl.runOnCompletion(
                      closeBackupStore(),
                      (ignoredClose, ignoredCloseError) ->
                          result.completeExceptionally(
                              new IllegalStateException("No partitions recovered", startError)));
                } else if (deactivateError != null) {
                  result.completeExceptionally(deactivateError);
                } else {
                  result.complete(null);
                }
              });
        });
  }

  private RecoveryPartitionStartupContext startupContext(
      final PartitionMetadata partitionMetadata) {
    final var partitionId = partitionMetadata.id();
    final var partitionDir = partitionDirectory(partitionId);
    return new RecoveryPartitionStartupContext(
        partitionId,
        partitionDir,
        actorSchedulingService,
        topologyManager,
        meterRegistry,
        concurrencyControl,
        brokerCfg,
        brokerInfo,
        gatewayBrokerTransport,
        backupStore);
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
          }
          concurrencyControl.runOnCompletion(
              closeBackupStore(),
              (ignoredClose, ignoredCloseError) -> {
                if (stopError != null) {
                  result.completeExceptionally(stopError);
                } else {
                  result.complete(null);
                }
              });
        });
  }

  private Path partitionDirectory(final PartitionId partitionId) {
    final var dataDirectory = brokerCfg.getData().getDirectory();
    return RaftPartitionFactory.getPartitionDirectory(partitionId, dataDirectory);
  }

  private MemberId localMemberId() {
    return membershipService.getLocalMember().id();
  }

  /**
   * Resolves the partitions of this partition group that the local broker is a member of, according
   * to the current partition distribution.
   */
  private List<PartitionMetadata> localPartitions() {
    final var localMemberId = localMemberId();
    // The default physical tenant's partition distribution is the only one stored in dynamic
    // config; other physical tenants derive their distribution by rewriting the group on every
    // PartitionId.
    return clusterConfigurationService
        .getPartitionDistribution()
        .withGroupName(partitionGroup)
        .partitions()
        .stream()
        .filter(p -> p.members().contains(localMemberId))
        .toList();
  }

  private ActorFuture<Void> closeBackupStore() {
    final var closed = concurrencyControl.<Void>createFuture();
    if (backupStore == null) {
      closed.complete(null);
      return closed;
    }
    final var store = backupStore;
    backupStore = null;
    store
        .closeAsync()
        .whenCompleteAsync(
            (ignore, closeError) -> {
              if (closeError != null) {
                LOG.error("Failed to close backup store", closeError);
              }
              closed.complete(null);
            },
            concurrencyControl);
    return closed;
  }

  private ActorFuture<Void> startPartition(final RecoveryPartition partition) {
    return partition
        .start()
        .andThen(
            (started, startError) -> {
              if (startError == null) {
                recoveryPartitions.add(started);
                return CompletableActorFuture.completed();
              }
              LOG.error(
                  "Failed to start recovery partition for {}, stopping it",
                  partition.partitionId(),
                  startError);
              failedPartitionIds.add(partition.partitionId().number());
              return partition
                  .stop()
                  .andThen(
                      (ignored, stopError) -> {
                        if (stopError != null) {
                          LOG.error(
                              "Failed to stop partially started recovery partition for {}",
                              partition.partitionId(),
                              stopError);
                        }
                        return CompletableActorFuture.<Void>completedExceptionally(startError);
                      },
                      concurrencyControl);
            },
            concurrencyControl);
  }

  @Override
  public ActorFuture<Void> join(
      final int partitionId,
      final Map<MemberId, Integer> membersWithPriority,
      final DynamicPartitionConfig partitionConfig) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform join on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> leave(final int partitionId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform leave on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> bootstrap(
      final int partitionId,
      final int priority,
      final DynamicPartitionConfig partitionConfig,
      final boolean initializeFromConfig) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform bootstrap on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> reconfigurePriority(final int partitionId, final int newPriority) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform reconfigurePriority on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> forceReconfigure(
      final int partitionId, final Collection<MemberId> members) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform forceReconfigure on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> disableExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform disableExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform deleteExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> enableExporter(
      final int partitionId,
      final String exporterId,
      final long metadataVersion,
      final String initializeFrom) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform enableExporter on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> setExportingState(
      final int partitionId, final ExportingState exportingState) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform setExportingState on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> initiateScaleUp(final int desiredPartitionCount) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform scaleUp on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> awaitRedistributionCompletion(
      final int desiredPartitionCount,
      final Set<Integer> redistributedPartitions,
      final Duration timeout) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException(
            "Cannot perform awaitRedistributionCompletion on a recovering partition"));
  }

  @Override
  public ActorFuture<Void> notifyPartitionBootstrapped(final int partitionId) {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException(
            "Cannot perform notifyPartitionBootstrapped on a recovering partition"));
  }

  @Override
  public ActorFuture<RoutingState> getRoutingState() {
    return CompletableActorFuture.completedExceptionally(
        new IllegalStateException("Cannot perform getRoutingState on a recovering partition"));
  }
}
