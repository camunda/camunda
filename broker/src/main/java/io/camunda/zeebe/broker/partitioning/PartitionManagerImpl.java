/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.raft.partition.RaftStorageConfig;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.PartitionHealthBroadcaster;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl implements PartitionManager, TopologyManager {

  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionManagerImpl.class);

  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private final RaftPartitionGroup partitionGroup;
  private final TopologyManagerImpl topologyManager;

  private final List<ZeebePartition> partitions = new CopyOnWriteArrayList<>();
  private final Map<Integer, PartitionAdminAccess> adminAccess = new ConcurrentHashMap<>();
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final ConcurrencyControl concurrencyControl;
  private final PartitionFactory partitionFactory;
  private final DefaultPartitionManagementService managementService;

  public PartitionManagerImpl(
      final ConcurrencyControl concurrencyControl,
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final ClusterServices clusterServices,
      final BrokerHealthCheckService healthCheckService,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final List<PartitionListener> partitionListeners,
      final CommandApiService commandApiService,
      final ExporterRepository exporterRepository,
      final AtomixServerTransport gatewayBrokerTransport,
      final JobStreamer jobStreamer,
      final PartitionDistribution partitionDistribution) {
    this.concurrencyControl = concurrencyControl;

    this.actorSchedulingService = actorSchedulingService;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    final var snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(actorSchedulingService, localBroker.getNodeId());
    final var featureFlags = brokerCfg.getExperimental().getFeatures().toFeatureFlags();

    partitionGroup =
        new RaftPartitionGroupFactory()
            .buildRaftPartitionGroup(brokerCfg, partitionDistribution, snapshotStoreFactory);
    topologyManager = new TopologyManagerImpl(clusterServices.getMembershipService(), localBroker);

    final List<PartitionListener> listeners = new ArrayList<>(partitionListeners);
    listeners.add(topologyManager);

    partitionFactory =
        new PartitionFactory(
            actorSchedulingService,
            brokerCfg,
            localBroker,
            commandApiService,
            snapshotStoreFactory,
            clusterServices,
            exporterRepository,
            healthCheckService,
            diskSpaceUsageMonitor,
            gatewayBrokerTransport,
            jobStreamer,
            listeners,
            topologyManager,
            featureFlags);
    managementService =
        new DefaultPartitionManagementService(
            clusterServices.getMembershipService(), clusterServices.getCommunicationService());
  }

  public PartitionAdminAccess createAdminAccess(final ConcurrencyControl concurrencyControl) {
    return new MultiPartitionAdminAccess(concurrencyControl, adminAccess);
  }

  public CompletableFuture<Void> start() {
    LOGGER.info("Starting partitions");

    actorSchedulingService.submitActor(topologyManager);
    healthCheckService.registerPartitionManager(this);

    final var memberId = managementService.getMembershipService().getLocalMember().id();
    partitionGroup
        .join(managementService)
        .forEach(
            (partitionId, partitionStart) ->
                partitionStart
                    .thenAcceptAsync(
                        partition -> {
                          if (!partition.members().contains(memberId)) {
                            return;
                          }
                          LOGGER.info("Started raft partition {}", partitionId);
                          startPartition(partitionFactory.constructPartition(partition));
                        })
                    .exceptionally(
                        error -> {
                          LOGGER.error("Failed to start raft partition {}", partitionId, error);
                          onHealthChanged(partitionId, HealthStatus.DEAD);
                          return null;
                        }));

    return CompletableFuture.completedFuture(null);
  }

  private void startPartition(final ZeebePartition zeebePartition) {
    final var partitionId = zeebePartition.getPartitionId();
    final var submit = actorSchedulingService.submitActor(zeebePartition);
    concurrencyControl.run(
        () ->
            concurrencyControl.runOnCompletion(
                submit,
                (ok, error) -> {
                  if (error != null) {
                    LOGGER.error("Failed to start Zeebe partition {}", partitionId, error);
                    onHealthChanged(partitionId, HealthStatus.DEAD);
                    return;
                  }

                  LOGGER.info("Started Zeebe partition {}", partitionId);

                  zeebePartition.addFailureListener(
                      new PartitionHealthBroadcaster(partitionId, this::onHealthChanged));
                  diskSpaceUsageMonitor.addDiskUsageListener(zeebePartition);
                  adminAccess.put(partitionId, zeebePartition.getAdminAccess());
                  partitions.add(zeebePartition);
                }));
  }

  public CompletableFuture<Void> stop() {
    return stopPartitions()
        .thenCompose((ignored) -> partitionGroup.close())
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                LOGGER.error(error.getMessage(), error);
              }
              partitions.clear();
              adminAccess.clear();
              topologyManager.close();
            });
  }

  private CompletableFuture<Void> stopPartitions() {
    final var futures =
        partitions.stream()
            .map(partition -> CompletableFuture.runAsync(() -> stopPartition(partition)))
            .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(futures);
  }

  private void stopPartition(final ZeebePartition partition) {
    diskSpaceUsageMonitor.removeDiskUsageListener(partition);
    healthCheckService.removeMonitoredPartition(partition.getPartitionId());
    partition.close();
  }

  @Override
  public String toString() {
    return "PartitionManagerImpl{"
        + ", partitionGroup="
        + partitionGroup
        + ", partitions="
        + partitions
        + '}';
  }

  public void onHealthChanged(final int i, final HealthStatus healthStatus) {
    topologyManager.onHealthChanged(i, healthStatus);
  }

  @Override
  public void removeTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.removeTopologyPartitionListener(listener);
  }

  @Override
  public void addTopologyPartitionListener(final TopologyPartitionListener listener) {
    topologyManager.addTopologyPartitionListener(listener);
  }

  @Override
  public RaftStorageConfig getRaftStorageConfig() {
    return partitionGroup.config().getStorageConfig();
  }

  @Override
  public RaftPartitionConfig getRaftPartitionConfig() {
    return partitionGroup.config().getPartitionConfig();
  }

  @Override
  public RaftPartition getRaftPartition(final int partitionId) {
    return partitionGroup.getPartition(partitionId);
  }

  @Override
  public Collection<RaftPartition> getRaftPartitions() {
    return partitionGroup.getPartitions().stream().map(RaftPartition.class::cast).toList();
  }

  @Override
  public Collection<ZeebePartition> getZeebePartitions() {
    return partitions;
  }
}
