/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;

final class BrokerContextImpl implements BrokerContext {

  private final ClusterServicesImpl clusterServices;
  private final EmbeddedGatewayService embeddedGatewayService;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final PartitionManager partitionManager;
  private final BrokerAdminService brokerAdminService;
  private final ManagedMessagingService apiMessagingService;

  BrokerContextImpl(
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final ClusterServicesImpl clusterServices,
      final EmbeddedGatewayService embeddedGatewayService,
      final PartitionManager partitionManager,
      final BrokerAdminService brokerAdminService,
      final ManagedMessagingService apiMessagingService) {
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.clusterServices = requireNonNull(clusterServices);
    this.embeddedGatewayService = embeddedGatewayService;
    this.partitionManager = requireNonNull(partitionManager);
    this.brokerAdminService = requireNonNull(brokerAdminService);
    this.apiMessagingService = requireNonNull(apiMessagingService);
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
  public PartitionManager getPartitionManager() {
    return partitionManager;
  }

  @Override
  public BrokerAdminService getBrokerAdminService() {
    return brokerAdminService;
  }

  @Override
  public ManagedMessagingService getApiMessagingService() {
    return apiMessagingService;
  }
}
