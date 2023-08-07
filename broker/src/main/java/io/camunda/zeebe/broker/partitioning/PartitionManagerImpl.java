/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.impl.DefaultPartitionManagementService;
import io.atomix.raft.partition.RaftPartitionGroup;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl implements PartitionManager, TopologyManager {

  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionManagerImpl.class);

  private volatile CompletableFuture<Void> closeFuture;
  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private RaftPartitionGroup partitionGroup;
  private TopologyManagerImpl topologyManager;

  private final List<ZeebePartition> partitions = new ArrayList<>();
  private final Map<Integer, PartitionAdminAccess> adminAccess = new ConcurrentHashMap<>();
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo localBroker;
  private final FileBasedSnapshotStoreFactory snapshotStoreFactory;
  private final List<PartitionListener> partitionListeners;
  private final ClusterServices clusterServices;
  private final CommandApiService commandApiService;
  private final ExporterRepository exporterRepository;
  private final AtomixServerTransport gatewayBrokerTransport;
  private final JobStreamer jobStreamer;

  public PartitionManagerImpl(
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
    this.gatewayBrokerTransport = gatewayBrokerTransport;

    snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(actorSchedulingService, localBroker.getNodeId());

    this.brokerCfg = brokerCfg;
    this.localBroker = localBroker;
    this.actorSchedulingService = actorSchedulingService;
    this.clusterServices = clusterServices;
    this.healthCheckService = healthCheckService;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.commandApiService = commandApiService;
    this.exporterRepository = exporterRepository;
    this.jobStreamer = jobStreamer;

    partitionGroup =
        new RaftPartitionGroupFactory()
            .buildRaftPartitionGroup(brokerCfg, partitionDistribution, snapshotStoreFactory);

    this.partitionListeners = new ArrayList<>(partitionListeners);
    topologyManager = new TopologyManagerImpl(clusterServices.getMembershipService(), localBroker);
    this.partitionListeners.add(topologyManager);
  }

  @Override
  public ManagedPartitionGroup getPartitionGroup() {
    return partitionGroup;
  }

  public PartitionAdminAccess createAdminAccess(final ConcurrencyControl concurrencyControl) {
    return new MultiPartitionAdminAccess(
        concurrencyControl,
        adminAccess);
  }

  public CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("PartitionManager is closed"));
    }

    LOGGER.info("Starting partitions");

    actorSchedulingService.submitActor(topologyManager);

    partitionGroup.join(
        new DefaultPartitionManagementService(
            clusterServices.getMembershipService(), clusterServices.getCommunicationService()));

    final var partitionFactory =
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
            jobStreamer);

    partitions.addAll(
        partitionFactory.constructPartitions(
            partitionGroup,
            partitionListeners,
            topologyManager,
            brokerCfg.getExperimental().getFeatures().toFeatureFlags()));
    partitions.forEach(this::startPartition);
    healthCheckService.registerPartitionManager(this);

    return CompletableFuture.completedFuture(null);
  }

  private void startPartition(final ZeebePartition zeebePartition) {
    actorSchedulingService
        .submitActor(zeebePartition)
        .onComplete(
            (ok, error) -> {
              if (error != null) {
                LOGGER.error(
                    "Failed to start partition {}", zeebePartition.getPartitionId(), error);
                return;
              }

              LOGGER.info("Started partition {}", zeebePartition.getPartitionId());

              zeebePartition.addFailureListener(
                  new PartitionHealthBroadcaster(
                      zeebePartition.getPartitionId(), this::onHealthChanged));
              diskSpaceUsageMonitor.addDiskUsageListener(zeebePartition);
              adminAccess.put(zeebePartition.getPartitionId(), zeebePartition.createAdminAccess());
            });
  }

  public CompletableFuture<Void> stop() {
    if (closeFuture == null) {
      closeFuture =
          CompletableFuture.runAsync(this::stopPartitions)
              .thenCompose((ignored) -> partitionGroup.close())
              .whenComplete(
                  (ok, error) -> {
                    logErrorIfApplicable(error);
                    partitionGroup = null;
                    partitions.clear();
                    adminAccess.clear();
                    topologyManager.close();
                    topologyManager = null;
                  });
    }

    return closeFuture;
  }

  private void logErrorIfApplicable(final Throwable error) {
    if (error != null) {
      LOGGER.error(error.getMessage(), error);
    }
  }

  private void stopPartitions() {
    final var futures =
        partitions.stream()
            .map(partition -> CompletableFuture.runAsync(() -> stopPartition(partition)))
            .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(futures).join();
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

  public List<ZeebePartition> getPartitions() {
    return partitions;
  }
}
