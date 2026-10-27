/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.messaging.ManagedMessagingService;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;
import java.util.Map;

/** Context for components/actors managed directly by the Broker */
public interface BrokerContext {

  ClusterServices getClusterServices();

  EmbeddedGatewayService getEmbeddedGatewayService();

  /**
   * Returns disk space usage monitor. May be {@code null} if disabled in configuration
   *
   * @return disk space usage monitor. May be {@code null} if disabled in configuration
   */
  DiskSpaceUsageMonitor getDiskSpaceUsageMonitor();

  PartitionManager getPartitionManager();

  /**
   * Returns the partition manager of every physical tenant running on this broker, keyed by
   * physical-tenant ID (including the {@code default} tenant). {@link #getPartitionManager()} only
   * ever exposes the default tenant's manager; this accessor exists mainly for test / introspection
   * access to non-default tenants' partitions.
   */
  Map<String, PartitionManager> getPartitionManagers();

  BrokerAdminService getBrokerAdminService();

  ManagedMessagingService getApiMessagingService();
}
