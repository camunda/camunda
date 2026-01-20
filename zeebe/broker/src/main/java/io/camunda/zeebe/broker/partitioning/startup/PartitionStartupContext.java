/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup;

import io.atomix.primitive.partition.PartitionManagementService;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManager;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStore;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransfer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.nio.file.Path;

public class PartitionStartupContext {
  private final String partitionGroup;
  private final ActorSchedulingService schedulingService;
  private final TopologyManager topologyManager;
  private final ConcurrencyControl concurrencyControl;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final BrokerHealthCheckService healthCheckService;
  private final PartitionManagementService partitionManagementService;
  private final PartitionMetadata partitionMetadata;
  private final RaftPartitionFactory raftPartitionFactory;
  private final ZeebePartitionFactory zeebePartitionFactory;
  private final BrokerCfg brokerConfig;
  private final DynamicPartitionConfig initialPartitionConfig;
  private final boolean initializeFromSnapshot;
  private final MeterRegistry brokerMeterRegistry;
  private final BrokerClient brokerClient;

  private Path partitionDirectory;

  private CompositeMeterRegistry partitionMeterRegistry;
  private FileBasedSnapshotStore snapshotStore;
  private RaftPartition raftPartition;
  private ZeebePartition zeebePartition;
  private SnapshotTransfer snapshotTransfer;

  public PartitionStartupContext(
      final String partitionGroup,
      final ActorSchedulingService schedulingService,
      final ConcurrencyControl concurrencyControl,
      final TopologyManager topologyManager,
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final BrokerHealthCheckService healthCheckService,
      final PartitionManagementService partitionManagementService,
      final PartitionMetadata partitionMetadata,
      final RaftPartitionFactory raftPartitionFactory,
      final ZeebePartitionFactory zeebePartitionFactory,
      final BrokerCfg brokerConfig,
      final DynamicPartitionConfig initialPartitionConfig,
      final boolean initializeFromSnapshot,
      final MeterRegistry brokerMeterRegistry,
      final BrokerClient brokerClient) {
    this.partitionGroup = partitionGroup;
    this.schedulingService = schedulingService;
    this.topologyManager = topologyManager;
    this.concurrencyControl = concurrencyControl;
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.healthCheckService = healthCheckService;
    this.partitionManagementService = partitionManagementService;
    this.partitionMetadata = partitionMetadata;
    this.raftPartitionFactory = raftPartitionFactory;
    this.zeebePartitionFactory = zeebePartitionFactory;
    this.brokerConfig = brokerConfig;
    this.initialPartitionConfig = initialPartitionConfig;
    this.initializeFromSnapshot = initializeFromSnapshot;
    this.brokerMeterRegistry = brokerMeterRegistry;
    this.brokerClient = brokerClient;
  }

  @Override
  public String toString() {
    return "PartitionStartupContext{" + "partition=" + partitionMetadata.id().id() + '}';
  }

  public Integer partitionId() {
    return partitionMetadata.id().id();
  }

  public ActorSchedulingService schedulingService() {
    return schedulingService;
  }

  public ConcurrencyControl concurrencyControl() {
    return concurrencyControl;
  }

  public TopologyManager topologyManager() {
    return topologyManager;
  }

  public DiskSpaceUsageMonitor diskSpaceUsageMonitor() {
    return diskSpaceUsageMonitor;
  }

  public BrokerHealthCheckService brokerHealthCheckService() {
    return healthCheckService;
  }

  public PartitionManagementService partitionManagementService() {
    return partitionManagementService;
  }

  public PartitionMetadata partitionMetadata() {
    return partitionMetadata;
  }

  public RaftPartitionFactory raftPartitionFactory() {
    return raftPartitionFactory;
  }

  public ZeebePartitionFactory zeebePartitionFactory() {
    return zeebePartitionFactory;
  }

  public Path partitionDirectory() {
    return partitionDirectory;
  }

  public FileBasedSnapshotStore snapshotStore() {
    return snapshotStore;
  }

  public PartitionStartupContext snapshotStore(final FileBasedSnapshotStore snapshotStore) {
    this.snapshotStore = snapshotStore;
    return this;
  }

  public PartitionStartupContext raftPartition(final RaftPartition raftPartition) {
    this.raftPartition = raftPartition;
    return this;
  }

  public RaftPartition raftPartition() {
    return raftPartition;
  }

  public PartitionStartupContext zeebePartition(final ZeebePartition zeebePartition) {
    this.zeebePartition = zeebePartition;
    return this;
  }

  public ZeebePartition zeebePartition() {
    return zeebePartition;
  }

  public BrokerCfg brokerConfig() {
    return brokerConfig;
  }

  public PartitionStartupContext partitionDirectory(final Path partitionDirectory) {
    this.partitionDirectory = partitionDirectory;
    return this;
  }

  public DynamicPartitionConfig initialPartitionConfig() {
    return initialPartitionConfig;
  }

  public PartitionStartupContext partitionMeterRegistry(
      final CompositeMeterRegistry partitionMeterRegistry) {
    this.partitionMeterRegistry = partitionMeterRegistry;
    return this;
  }

  public CompositeMeterRegistry partitionMeterRegistry() {
    return partitionMeterRegistry;
  }

  public MeterRegistry brokerMeterRegistry() {
    return brokerMeterRegistry;
  }

  public boolean isInitializeFromSnapshot() {
    return initializeFromSnapshot;
  }

  public BrokerClient brokerClient() {
    return brokerClient;
  }

  public void setSnapshotTransfer(final SnapshotTransfer snapshotTransfer) {
    this.snapshotTransfer = snapshotTransfer;
  }

  public SnapshotTransfer snapshotTransfer() {
    return snapshotTransfer;
  }

  public String getPartitionGroup() {
    return partitionGroup;
  }
}
