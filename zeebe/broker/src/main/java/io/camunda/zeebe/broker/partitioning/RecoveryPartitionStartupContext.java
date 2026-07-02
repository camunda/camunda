/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.management.ReadOnlyBackupService;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.transport.backupapi.ReadOnlyBackupApiRequestHandler;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;

/** Carries per-partition state through the recovery startup and shutdown steps. */
public final class RecoveryPartitionStartupContext {

  private final PartitionId partitionId;
  private final Path partitionDirectory;
  private final ActorSchedulingService schedulingService;
  private final MeterRegistry meterRegistry;
  private final TopologyManagerImpl topologyManager;
  private final ConcurrencyControl concurrencyControl;
  private final BrokerCfg brokerCfg;
  private final BrokerInfo brokerInfo;
  private final AtomixServerTransport gatewayBrokerTransport;
  private BackupStore backupStore;
  private ReadOnlyBackupService backupService;
  private ReadOnlyBackupApiRequestHandler backupApiRequestHandler;

  public RecoveryPartitionStartupContext(
      final PartitionId partitionId,
      final Path partitionDirectory,
      final ActorSchedulingService schedulingService,
      final TopologyManagerImpl topologyManager,
      final MeterRegistry meterRegistry,
      final ConcurrencyControl concurrencyControl,
      final BrokerCfg brokerCfg,
      final BrokerInfo brokerInfo,
      final AtomixServerTransport gatewayBrokerTransport) {
    this.partitionId = partitionId;
    this.partitionDirectory = partitionDirectory;
    this.schedulingService = schedulingService;
    this.meterRegistry = meterRegistry;
    this.topologyManager = topologyManager;
    this.concurrencyControl = concurrencyControl;
    this.brokerCfg = brokerCfg;
    this.brokerInfo = brokerInfo;
    this.gatewayBrokerTransport = gatewayBrokerTransport;
  }

  @Override
  public String toString() {
    return "RecoveryPartitionStartupContext{partition=" + partitionId + '}';
  }

  public PartitionId partitionId() {
    return partitionId;
  }

  public Path partitionDirectory() {
    return partitionDirectory;
  }

  public ActorSchedulingService schedulingService() {
    return schedulingService;
  }

  public MeterRegistry meterRegistry() {
    return meterRegistry;
  }

  public TopologyManagerImpl topologyManager() {
    return topologyManager;
  }

  public ConcurrencyControl getConcurrencyControl() {
    return concurrencyControl;
  }

  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  public BrokerInfo getBrokerInfo() {
    return brokerInfo;
  }

  public AtomixServerTransport getGatewayBrokerTransport() {
    return gatewayBrokerTransport;
  }

  public BackupStore getBackupStore() {
    return backupStore;
  }

  public RecoveryPartitionStartupContext setBackupStore(final BackupStore backupStore) {
    this.backupStore = backupStore;
    return this;
  }

  public ReadOnlyBackupService getBackupService() {
    return backupService;
  }

  public RecoveryPartitionStartupContext setBackupService(
      final ReadOnlyBackupService backupService) {
    this.backupService = backupService;
    return this;
  }

  public ReadOnlyBackupApiRequestHandler getBackupApiRequestHandler() {
    return backupApiRequestHandler;
  }

  public RecoveryPartitionStartupContext setBackupApiRequestHandler(
      final ReadOnlyBackupApiRequestHandler backupApiRequestHandler) {
    this.backupApiRequestHandler = backupApiRequestHandler;
    return this;
  }
}
