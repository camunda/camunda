/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.scaling.BrokerClientPartitionScalingExecutor;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.startup.ZeebePartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupProcessShutdownException;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl
    implements PartitionManager, PartitionChangeExecutor, PartitionScalingChangeExecutor {

  public static final String GROUP_NAME = "raft-partition";
  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionManagerImpl.class);
  private final ConcurrencyControl concurrencyControl;

  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private final TopologyManagerImpl topologyManager;
  private final Map<Integer, Partition> partitions = new ConcurrentHashMap<>();
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final BrokerClient brokerClient;
  private final SnapshotApiRequestHandler snapshotApiRequestHandler;
  private final DefaultPartitionManagementService managementService;
  private final BrokerCfg brokerCfg;
  private final ZeebePartitionFactory zeebePartitionFactory;
  private final RaftPartitionFactory raftPartitionFactory;
  private final ClusterConfigurationService clusterConfigurationService;
  private final MeterRegistry brokerMeterRegistry;
  private final PartitionScalingChangeExecutor scalingExecutor;

  public PartitionManagerImpl(
      final ConcurrencyControl concurrencyControl,
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final ClusterServices clusterServices,
      final BrokerHealthCheckService healthCheckService,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final List<PartitionListener> partitionListeners,
      final List<PartitionRaftListener> partitionRaftListeners,
      final CommandApiService commandApiService,
      final SnapshotApiRequestHandler snapshotApiRequestHandler,
      final ExporterRepository exporterRepository,
      final AtomixServerTransport gatewayBrokerTransport,
      final JobStreamer jobStreamer,
      final ClusterConfigurationService clusterConfigurationService,
      final MeterRegistry meterRegistry,
      final BrokerClient brokerClient,
      final SecurityConfiguration securityConfig,
      final SearchClientsProxy searchClientsProxy) {
    this.brokerCfg = brokerCfg;
    this.concurrencyControl = concurrencyControl;
    this.actorSchedulingService = actorSchedulingService;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.brokerClient = brokerClient;
    this.snapshotApiRequestHandler = snapshotApiRequestHandler;
    scalingExecutor = new BrokerClientPartitionScalingExecutor(brokerClient, concurrencyControl);
    final var featureFlags = brokerCfg.getExperimental().getFeatures().toFeatureFlags();
    this.clusterConfigurationService = clusterConfigurationService;
    brokerMeterRegistry = meterRegistry;
    // TODO: Do this as a separate step before starting the partition manager
    topologyManager = new TopologyManagerImpl(clusterServices.getMembershipService(), localBroker);

    final List<PartitionListener> listeners = new ArrayList<>(partitionListeners);
    listeners.add(topologyManager);

    zeebePartitionFactory =
        new ZeebePartitionFactory(
            actorSchedulingService,
            brokerCfg,
            localBroker,
            commandApiService,
            snapshotApiRequestHandler,
            clusterServices,
            exporterRepository,
            diskSpaceUsageMonitor,
            gatewayBrokerTransport,
            jobStreamer,
            listeners,
            partitionRaftListeners,
            topologyManager,
            featureFlags,
            securityConfig,
            searchClientsProxy);
    managementService =
        new DefaultPartitionManagementService(
            clusterServices.getMembershipService(), clusterServices.getCommunicationService());
    raftPartitionFactory = new RaftPartitionFactory(brokerCfg);
  }

  public void start() {
    actorSchedulingService.submitActor(topologyManager);
    final var localMemberId = managementService.getMembershipService().getLocalMember().id();
    final var memberPartitions =
        clusterConfigurationService.getPartitionDistribution().partitions().stream()
            .filter(p -> p.members().contains(localMemberId))
            .toList();

    healthCheckService.registerBootstrapPartitions(memberPartitions);
    for (final var partitionMetadata : memberPartitions) {
      final var initialPartitionConfig =
          clusterConfigurationService
              .getInitialClusterConfiguration()
              .members()
              .get(localMemberId)
              .getPartition(partitionMetadata.id().id())
              .config();
      bootstrapPartition(partitionMetadata, initialPartitionConfig, false);
    }
  }

  private ActorFuture<Void> bootstrapPartition(
      final PartitionMetadata partitionMetadata,
      final DynamicPartitionConfig initialPartitionConfig,
      final boolean initializeFromSnapshot) {
    final var result = concurrencyControl.<Void>createFuture();
    final var id = partitionMetadata.id().id();
    final var context =
        new PartitionStartupContext(
            actorSchedulingService,
            concurrencyControl,
            topologyManager,
            diskSpaceUsageMonitor,
            healthCheckService,
            managementService,
            partitionMetadata,
            raftPartitionFactory,
            zeebePartitionFactory,
            brokerCfg,
            initialPartitionConfig,
            initializeFromSnapshot,
            brokerMeterRegistry,
            brokerClient);
    final var partition = Partition.bootstrapping(context);
    if (partitions.putIfAbsent(id, partition) != null) {
      result.completeExceptionally(
          new PartitionAlreadyExistsException(partitionMetadata.id().id()));
    } else {
      concurrencyControl.runOnCompletion(
          partition.start(), (started, error) -> completePartitionStart(id, error, result));
    }

    return result;
  }

  private ActorFuture<Void> joinPartition(
      final PartitionMetadata partitionMetadata,
      final DynamicPartitionConfig initialPartitionConfig) {
    final var result = concurrencyControl.<Void>createFuture();
    final var id = partitionMetadata.id().id();
    final var context =
        new PartitionStartupContext(
            actorSchedulingService,
            concurrencyControl,
            topologyManager,
            diskSpaceUsageMonitor,
            healthCheckService,
            managementService,
            partitionMetadata,
            raftPartitionFactory,
            zeebePartitionFactory,
            brokerCfg,
            initialPartitionConfig,
            false,
            brokerMeterRegistry,
            brokerClient);
    final var partition = Partition.joining(context);
    final var previousPartition = partitions.putIfAbsent(id, partition);
    if (previousPartition != null) {
      result.completeExceptionally(
          new IllegalStateException(String.format("Partition %d already exists", id)));
      return result;
    }
    concurrencyControl.run(
        () ->
            concurrencyControl.runOnCompletion(
                partition.start(), (started, error) -> completePartitionStart(id, error, result)));
    return result;
  }

  private void completePartitionStart(
      final int partitionId, final Throwable error, final ActorFuture<Void> future) {

    if (error != null) {
      // If Partition start was not complete due to a shutdown being called
      // during the startup process, then this shouldn't be logged as an error
      if (error instanceof StartupProcessShutdownException) {
        LOGGER.warn("Aborting startup of partition {}", partitionId, error);
      } else {
        LOGGER.error(
            "Failed to start partition {}, removing partition and shutting down already started steps",
            partitionId,
            error);
        concurrencyControl.runOnCompletion(
            partitions.remove(partitionId).stop(),
            (stopped, stopError) -> {
              topologyManager.onHealthChanged(partitionId, HealthStatus.DEAD);
              if (stopError != null) {
                LOGGER.error(
                    "Partition {} already failed during startup, now shutdown failed too",
                    partitionId,
                    error);
              }
            });
      }

      future.completeExceptionally(error);
      return;
    }

    LOGGER.info("Started partition {}", partitionId);
    future.complete(null);
  }

  public ActorFuture<Void> stop() {
    final var result = concurrencyControl.<Void>createFuture();
    final var stop =
        partitions.values().stream()
            .map(Partition::stop)
            .collect(new ActorFutureCollector<>(concurrencyControl));
    concurrencyControl.runOnCompletion(
        stop,
        (ok, error) -> {
          if (error != null) {
            LOGGER.error("Failed to stop partitions", error);
            result.completeExceptionally(error);
          } else {
            partitions.clear();
            topologyManager.closeAsync().onComplete(result);
          }
        });
    return result;
  }

  @Override
  public String toString() {
    return "PartitionManagerImpl{partitions=" + partitions + '}';
  }

  @Override
  public RaftPartition getRaftPartition(final int partitionId) {
    final var partition = partitions.get(partitionId);
    return partition == null ? null : partition.raftPartition();
  }

  @Override
  public Collection<RaftPartition> getRaftPartitions() {
    return partitions.values().stream()
        .map(Partition::raftPartition)
        // raftPartition may be null before the partition is fully started
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public Collection<ZeebePartition> getZeebePartitions() {
    return partitions.values().stream()
        .map(Partition::zeebePartition)
        // zeebePartition may be null before the partition is fully started
        .filter(Objects::nonNull)
        .toList();
  }

  @Override
  public ActorFuture<Void> join(
      final int partitionId,
      final Map<MemberId, Integer> membersWithPriority,
      final DynamicPartitionConfig partitionConfig) {
    final int targetPriority = Collections.max(membersWithPriority.values());

    final var members = membersWithPriority.keySet();
    final var primaries =
        membersWithPriority.entrySet().stream()
            .filter(entry -> entry.getValue() == targetPriority)
            .map(Entry::getKey)
            .toList();

    MemberId primary = null;
    if (primaries.size() == 1) {
      primary = primaries.get(0);
    }

    final var partitionMetadata =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, partitionId),
            members,
            membersWithPriority,
            targetPriority,
            primary);

    return joinPartition(partitionMetadata, partitionConfig); // TODO
  }

  @Override
  public ActorFuture<Void> leave(final int partitionId) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var partition = partitions.get(partitionId);
          if (partition == null) {
            result.completeExceptionally(
                new IllegalArgumentException("No partition with id %s".formatted(partitionId)));
            return;
          }
          LOGGER.info("Leaving partition {}", partitionId);
          concurrencyControl.runOnCompletion(
              partition.leave(),
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                  return;
                }

                partitions.remove(partitionId);
                LOGGER.info("Left partition {}", partitionId);
                result.complete(null);
              });
        });
    return result;
  }

  @Override
  public ActorFuture<Void> bootstrap(
      final int partitionId,
      final int priority,
      final DynamicPartitionConfig partitionConfig,
      final boolean initializeFromSnapshot) {
    final int targetPriority = priority;

    final MemberId localMember = managementService.getMembershipService().getLocalMember().id();
    final var members = Set.of(localMember);

    final MemberId primary = localMember;

    final var partitionMetadata =
        new PartitionMetadata(
            PartitionId.from(GROUP_NAME, partitionId),
            members,
            Map.of(localMember, targetPriority),
            targetPriority,
            primary);

    final ActorFuture<Void> future = concurrencyControl.createFuture();

    concurrencyControl.run(
        () ->
            bootstrapPartition(partitionMetadata, partitionConfig, initializeFromSnapshot)
                .onComplete(future));
    if (!initializeFromSnapshot) {
      return future;
    }
    return future
        .andThen(ignored -> notifyPartitionBootstrapped(partitionId), concurrencyControl)
        .andThen(
            (ignored, error) -> {
              if (error != null) {
                if (error instanceof PartitionAlreadyExistsException) {
                  return CompletableActorFuture.completedExceptionally(error);
                } else {
                  // bootstrap has failed, let's stop and return the error.
                  return stopPartition(partitionId)
                      .andThen(
                          none -> CompletableActorFuture.completedExceptionally(error),
                          concurrencyControl);
                }
              } else {
                return CompletableActorFuture.completed();
              }
            },
            concurrencyControl);
  }

  @Override
  public ActorFuture<Void> reconfigurePriority(final int partitionId, final int newPriority) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var partition = partitions.get(partitionId);
          if (partition == null) {
            result.completeExceptionally(
                new IllegalArgumentException("No partition with id %s".formatted(partitionId)));
            return;
          }
          LOGGER.info("Reconfiguring priority of partition {} to {}", partitionId, newPriority);
          concurrencyControl.runOnCompletion(
              partition.reconfigurePriority(newPriority),
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                  return;
                }

                LOGGER.info(
                    "Reconfigured priority of partition {} to {}", partitionId, newPriority);
                result.complete(null);
              });
        });

    return result;
  }

  @Override
  public ActorFuture<Void> forceReconfigure(
      final int partitionId, final Collection<MemberId> members) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> {
          final var partition = partitions.get(partitionId);
          if (partition == null) {
            result.completeExceptionally(
                new IllegalArgumentException("No partition with id %s".formatted(partitionId)));
            return;
          }
          LOGGER.info("Force reconfiguring partition {} with members {}", partitionId, members);
          concurrencyControl.runOnCompletion(
              partition.forceReconfigure(members),
              (ok, error) -> {
                if (error != null) {
                  result.completeExceptionally(error);
                  return;
                }

                LOGGER.info(
                    "Force reconfigured partition {} with members {}", partitionId, members);
                result.complete(null);
              });
        });
    return result;
  }

  @Override
  public ActorFuture<Void> disableExporter(final int partitionId, final String exporterId) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(() -> disableExporter(partitionId, exporterId, result));
    return result;
  }

  @Override
  public ActorFuture<Void> deleteExporter(final int partitionId, final String exporterId) {
    throw new UnsupportedOperationException("deleteExporter not yet implemented");
  }

  @Override
  public ActorFuture<Void> enableExporter(
      final int partitionId,
      final String exporterId,
      final long metadataVersion,
      final String initializeFrom) {
    final var result = concurrencyControl.<Void>createFuture();
    concurrencyControl.run(
        () -> enableExporter(partitionId, exporterId, metadataVersion, initializeFrom, result));
    return result;
  }

  private void disableExporter(
      final int partitionId, final String exporterId, final ActorFuture<Void> result) {
    final var partition = partitions.get(partitionId);
    if (partition == null) {
      result.completeExceptionally(
          new IllegalArgumentException("No partition with id %s".formatted(partitionId)));
      return;
    }

    if (partition.zeebePartition() == null) {
      result.completeExceptionally(
          new IllegalArgumentException(
              "Expected to disable exporter on partition %s, but zeebePartition is not ready"
                  .formatted(partitionId)));
      return;
    }
    LOGGER.trace("Disabling exporter {} on partition {}", exporterId, partitionId);
    concurrencyControl.runOnCompletion(
        partition.zeebePartition().disableExporter(exporterId),
        (ok, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
            return;
          }

          LOGGER.info("Disabled exporter {} on partition {}", exporterId, partitionId);
          result.complete(null);
        });
  }

  private void enableExporter(
      final int partitionId,
      final String exporterId,
      final long metadataVersion,
      final String initializeFrom,
      final ActorFuture<Void> result) {
    final var partition = partitions.get(partitionId);
    if (partition == null) {
      result.completeExceptionally(
          new IllegalArgumentException("No partition with id %s".formatted(partitionId)));
      return;
    }

    if (partition.zeebePartition() == null) {
      result.completeExceptionally(
          new IllegalArgumentException(
              "Expected to enable exporter on partition %s, but zeebePartition is not ready"
                  .formatted(partitionId)));
      return;
    }
    LOGGER.trace(
        "Enabling exporter {} on partition {} with metadata version {} and initializing from {}",
        exporterId,
        partitionId,
        metadataVersion,
        initializeFrom);
    concurrencyControl.runOnCompletion(
        partition.zeebePartition().enableExporter(exporterId, metadataVersion, initializeFrom),
        (ok, error) -> {
          if (error != null) {
            result.completeExceptionally(error);
            return;
          }

          LOGGER.info(
              "Enabled exporter {} on partition {} with metadata version {} and initializing from {}",
              exporterId,
              partitionId,
              metadataVersion,
              initializeFrom);
          result.complete(null);
        });
  }

  @Override
  public ActorFuture<Void> initiateScaleUp(final int desiredPartitionCount) {
    return scalingExecutor.initiateScaleUp(desiredPartitionCount);
  }

  @Override
  public ActorFuture<Void> awaitRedistributionCompletion(
      final int desiredPartitionCount,
      final Set<Integer> redistributedPartitions,
      final Duration timeout) {
    return scalingExecutor.awaitRedistributionCompletion(
        desiredPartitionCount, redistributedPartitions, timeout);
  }

  @Override
  public ActorFuture<Void> notifyPartitionBootstrapped(final int partitionId) {
    return scalingExecutor.notifyPartitionBootstrapped(partitionId);
  }

  @Override
  public ActorFuture<RoutingState> getRoutingState() {
    return scalingExecutor.getRoutingState();
  }

  private ActorFuture<Void> stopPartition(final int partitionId) {
    final var partition = partitions.get(partitionId);
    if (partition != null) {
      return partition
          .stop()
          .thenApply(
              ignored -> {
                partitions.remove(partitionId);
                return null;
              },
              concurrencyControl);
    } else {
      return CompletableActorFuture.completed();
    }
  }

  public final class PartitionAlreadyExistsException extends RuntimeException {

    PartitionAlreadyExistsException(final int partitionId) {
      super("Partition with id %d already exists".formatted(partitionId));
    }
  }
}
