/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.PartitionRaftListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory;
import io.camunda.zeebe.broker.partitioning.startup.ZeebePartitionFactory;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.ActorFutureCollector;
import io.camunda.zeebe.scheduler.startup.StartupProcessShutdownException;
import io.camunda.zeebe.topology.changes.PartitionChangeExecutor;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.HealthStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl implements PartitionManager, PartitionChangeExecutor {

  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionManagerImpl.class);
  private final ConcurrencyControl concurrencyControl;

  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private final TopologyManagerImpl topologyManager;
  private final Map<Integer, Partition> partitions = new ConcurrentHashMap<>();
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final PartitionDistribution partitionDistribution;
  private final DefaultPartitionManagementService managementService;
  private final BrokerCfg brokerCfg;
  private final ZeebePartitionFactory zeebePartitionFactory;
  private final RaftPartitionFactory raftPartitionFactory;
  private final MeterRegistry brokerMeterRegistry;

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
      final ExporterRepository exporterRepository,
      final AtomixServerTransport gatewayBrokerTransport,
      final JobStreamer jobStreamer,
      final PartitionDistribution partitionDistribution,
      final MeterRegistry brokerMeterRegistry) {
    this.brokerCfg = brokerCfg;
    this.concurrencyControl = concurrencyControl;
    this.actorSchedulingService = actorSchedulingService;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    final var featureFlags = brokerCfg.getExperimental().getFeatures().toFeatureFlags();
    this.partitionDistribution = partitionDistribution;
    this.brokerMeterRegistry = brokerMeterRegistry;
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
            clusterServices,
            exporterRepository,
            diskSpaceUsageMonitor,
            gatewayBrokerTransport,
            jobStreamer,
            listeners,
            partitionRaftListeners,
            topologyManager,
            partitionDistribution,
            featureFlags);
    managementService =
        new DefaultPartitionManagementService(
            clusterServices.getMembershipService(), clusterServices.getCommunicationService());
    raftPartitionFactory = new RaftPartitionFactory(brokerCfg);
  }

  public void start() {
    actorSchedulingService.submitActor(topologyManager);
    final var localMemberId = managementService.getMembershipService().getLocalMember().id();
    final var memberPartitions =
        partitionDistribution.partitions().stream()
            .filter(p -> p.members().contains(localMemberId))
            .toList();

    healthCheckService.registerBootstrapPartitions(memberPartitions);
    for (final var partitionMetadata : memberPartitions) {
      bootstrapPartition(partitionMetadata);
    }
  }

  private ActorFuture<Void> bootstrapPartition(final PartitionMetadata partitionMetadata) {
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
            brokerMeterRegistry);
    final var partition = Partition.bootstrapping(context);
    partitions.put(id, partition);

    concurrencyControl.runOnCompletion(
        partition.start(), (started, error) -> completePartitionStart(id, error, result));
    return result;
  }

  private ActorFuture<Void> joinPartition(final PartitionMetadata partitionMetadata) {
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
            brokerMeterRegistry);
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
    return partitions.get(partitionId).raftPartition();
  }

  @Override
  public Collection<RaftPartition> getRaftPartitions() {
    return partitions.values().stream().map(Partition::raftPartition).toList();
  }

  @Override
  public Collection<ZeebePartition> getZeebePartitions() {
    return partitions.values().stream().map(Partition::zeebePartition).toList();
  }

  @Override
  public ActorFuture<Void> join(
      final int partitionId, final Map<MemberId, Integer> membersWithPriority) {
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

    return joinPartition(partitionMetadata);
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
}
