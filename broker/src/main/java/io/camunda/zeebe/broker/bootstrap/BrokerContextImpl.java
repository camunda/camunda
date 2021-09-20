/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;

public final class BrokerContextImpl implements BrokerContext {

  private final ClusterServicesImpl clusterServices;
  private final EmbeddedGatewayService embeddedGatewayService;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final PartitionManagerImpl partitionManager;

  public BrokerContextImpl(
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final ClusterServicesImpl clusterServices,
      final EmbeddedGatewayService embeddedGatewayService,
      final PartitionManagerImpl partitionManager) {
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.clusterServices = requireNonNull(clusterServices);
    this.embeddedGatewayService = embeddedGatewayService;
    this.partitionManager = requireNonNull(partitionManager);
  }

  @Override
  public ClusterServicesImpl getClusterServices() {
    return clusterServices;
  }

  @Override
  public EmbeddedGatewayService getEmbeddedGatewayService() {
    return embeddedGatewayService;
  }

  @Override
  public DiskSpaceUsageMonitor getDiskSpaceUsageMonitor() {
    return diskSpaceUsageMonitor;
  }

  @Override
  public PartitionManagerImpl getPartitionManager() {
    return partitionManager;
  }
}
