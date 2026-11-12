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
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.PhysicalTenantModules;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import java.util.Map;

final class BrokerContextImpl implements BrokerContext {

  private final ClusterServicesImpl clusterServices;
  private final EmbeddedGatewayService embeddedGatewayService;
  private final DiskSpaceUsageMonitor diskSpaceUsageMonitor;
  private final Map<String, PartitionManager> partitionManagers;
  private final Map<String, ? extends BrokerAdminService> brokerAdminServices;
  private final ManagedMessagingService apiMessagingService;
  private final PhysicalTenantModules physicalTenantModules;
  private final ClusterConfigurationService clusterConfigurationService;

  BrokerContextImpl(
      final DiskSpaceUsageMonitor diskSpaceUsageMonitor,
      final ClusterServicesImpl clusterServices,
      final EmbeddedGatewayService embeddedGatewayService,
      final Map<String, PartitionManager> partitionManagers,
      final Map<String, ? extends BrokerAdminService> brokerAdminServices,
      final ManagedMessagingService apiMessagingService,
      final PhysicalTenantModules physicalTenantModules,
      final ClusterConfigurationService clusterConfigurationService) {
    this.diskSpaceUsageMonitor = diskSpaceUsageMonitor;
    this.clusterServices = requireNonNull(clusterServices);
    this.embeddedGatewayService = embeddedGatewayService;
    this.partitionManagers = requireNonNull(partitionManagers);
    this.brokerAdminServices = requireNonNull(brokerAdminServices);
    this.apiMessagingService = requireNonNull(apiMessagingService);
    this.physicalTenantModules = requireNonNull(physicalTenantModules);
    this.clusterConfigurationService = requireNonNull(clusterConfigurationService);
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
    return partitionManagers.get(PartitionManagerImpl.DEFAULT_GROUP_NAME);
  }

  @Override
  public Map<String, PartitionManager> getPartitionManagers() {
    return partitionManagers;
  }

  @Override
  public BrokerAdminService getBrokerAdminService() {
    return brokerAdminServices.get(PartitionManagerImpl.DEFAULT_GROUP_NAME);
  }

  @Override
  public ManagedMessagingService getApiMessagingService() {
    return apiMessagingService;
  }

  @Override
  public PhysicalTenantModules getPhysicalTenantModules() {
    return physicalTenantModules;
  }

  @Override
  public ClusterConfigurationService getClusterConfigurationService() {
    return clusterConfigurationService;
  }
}
