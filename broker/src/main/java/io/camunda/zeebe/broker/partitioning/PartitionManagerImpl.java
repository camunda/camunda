/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.primitive.partition.ManagedPartitionGroup;
import io.atomix.primitive.partition.ManagedPartitionService;
import io.atomix.primitive.partition.impl.DefaultPartitionService;
import io.atomix.raft.partition.RaftPartitionGroup;
import io.atomix.utils.concurrent.Futures;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.TopologyPartitionListener;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.management.deployment.PushDeploymentRequestHandler;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.system.partitions.PartitionHealthBroadcaster;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PartitionManagerImpl implements PartitionManager, TopologyManager {

  public static final String GROUP_NAME = "raft-partition";

  private static final Logger LOGGER = LoggerFactory.getLogger(PartitionManagerImpl.class);

  private volatile CompletableFuture<Void> closeFuture;
  private final BrokerHealthCheckService healthCheckService;
  private final ActorSchedulingService actorSchedulingService;
  private ManagedPartitionService partitionService;
  private RaftPartitionGroup partitionGroup;
  private TopologyManagerImpl topologyManager;

  private final List<ZeebePartition> partitions = new ArrayList<>();
  private final Consumer<DiskSpaceUsageListener> diskSpaceUsageListenerRegistry;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo localBroker;
  private final FileBasedSnapshotStoreFactory snapshotStoreFactory;
  private final PushDeploymentRequestHandler deploymentRequestHandler;
  private final List<PartitionListener> partitionListeners;
  private final ClusterServices clusterServices;
  private final CommandApiService commandApiService;
  private final ExporterRepository exporterRepository;

  public PartitionManagerImpl(
      final ActorSchedulingService actorSchedulingService,
      final BrokerCfg brokerCfg,
      final BrokerInfo localBroker,
      final ClusterServices clusterServices,
      final BrokerHealthCheckService healthCheckService,
      final PushDeploymentRequestHandler deploymentRequestHandler,
      final Consumer<DiskSpaceUsageListener> diskSpaceUsageListenerRegistry,
      final List<PartitionListener> partitionListeners,
      final CommandApiService commandApiService,
      final ExporterRepository exporterRepository) {

    snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(actorSchedulingService, localBroker.getNodeId());

    this.brokerCfg = brokerCfg;
    this.localBroker = localBroker;
    this.actorSchedulingService = actorSchedulingService;
    this.clusterServices = clusterServices;
    this.healthCheckService = healthCheckService;
    this.deploymentRequestHandler = deploymentRequestHandler;
    this.diskSpaceUsageListenerRegistry = diskSpaceUsageListenerRegistry;
    this.commandApiService = commandApiService;
    this.exporterRepository = exporterRepository;

    partitionGroup =
        new RaftPartitionGroupFactory().buildRaftPartitionGroup(brokerCfg, snapshotStoreFactory);

    final var membershipService = clusterServices.getMembershipService();
    final var communicationService = clusterServices.getCommunicationService();

    partitionService =
        new DefaultPartitionService(membershipService, communicationService, partitionGroup);

    topologyManager = new TopologyManagerImpl(membershipService, localBroker);
    partitionListeners.add(topologyManager);

    this.partitionListeners = partitionListeners;
  }

  @Override
  public ManagedPartitionGroup getPartitionGroup() {
    return partitionGroup;
  }

  public PartitionAdminAccess createAdminAccess(final ConcurrencyControl concurrencyControl) {
    final var adminAccess =
        new MultiPartitionAdminAccess(
            concurrencyControl,
            partitions.stream()
                .map(ZeebePartition::createAdminAccess)
                .collect(Collectors.toList()));
    return adminAccess;
  }

  public CompletableFuture<Void> start() {
    if (closeFuture != null) {
      return Futures.exceptionalFuture(new IllegalStateException("PartitionManager is closed"));
    }

    actorSchedulingService.submitActor(topologyManager);

    return partitionService
        .start()
        .thenApply(
            ps -> {
              LOGGER.info("Registering Partition Manager");

              /* this must be called here; it must be called after the members of the partition have been
              populated which happens during start of bootstrap manager and before the partitions are registered */
              healthCheckService.registerPartitionManager(this);

              LOGGER.info("Starting partitions");

              final var partitionFactory =
                  new PartitionFactory(
                      actorSchedulingService,
                      brokerCfg,
                      localBroker,
                      deploymentRequestHandler,
                      commandApiService,
                      snapshotStoreFactory,
                      clusterServices,
                      exporterRepository,
                      healthCheckService);

              partitions.addAll(
                  partitionFactory.constructPartitions(
                      partitionGroup, partitionListeners, topologyManager));

              final var futures =
                  partitions.stream()
                      .map(partition -> CompletableFuture.runAsync(() -> startPartition(partition)))
                      .toArray(CompletableFuture[]::new);

              CompletableFuture.allOf(futures).join();
              return null;
            });
  }

  private void startPartition(final ZeebePartition zeebePartition) {

    actorSchedulingService.submitActor(zeebePartition).join();
    zeebePartition.addFailureListener(
        new PartitionHealthBroadcaster(zeebePartition.getPartitionId(), this::onHealthChanged));
    diskSpaceUsageListenerRegistry.accept(zeebePartition);
  }

  public CompletableFuture<Void> stop() {
    if (closeFuture == null) {
      closeFuture =
          CompletableFuture.runAsync(this::stopPartitions)
              .whenComplete(
                  (ok, error) -> {
                    logErrorIfApplicable(error);
                    partitionService.stop().join();
                  })
              .whenComplete(
                  (ok, error) -> {
                    logErrorIfApplicable(error);
                    partitionGroup = null;
                    partitionService = null;
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
    healthCheckService.removeMonitoredPartition(partition.getPartitionId());
    partition.close();
  }

  @Override
  public String toString() {
    return "PartitionManagerImpl{"
        + "partitionService="
        + partitionService
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
