/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiService;
import java.util.Collection;
import java.util.List;

public final class BrokerContextImpl implements BrokerContext {

  private final ClusterServicesImpl clusterServices;
  private final CommandApiService commandApiService;
  private final EmbeddedGatewayService embeddedGatewayService;
  private final List<PartitionListener> partitionListeners;
  private final List<DiskSpaceUsageListener> diskSpaceUsageListeners;

  public BrokerContextImpl(
      final ClusterServicesImpl clusterServices,
      final CommandApiService commandApiService,
      final EmbeddedGatewayService embeddedGatewayService,
      final List<PartitionListener> partitionListeners,
      final List<DiskSpaceUsageListener> diskSpaceUsageListeners) {
    this.clusterServices = requireNonNull(clusterServices);
    this.commandApiService = requireNonNull(commandApiService);
    this.embeddedGatewayService = embeddedGatewayService;
    this.partitionListeners = unmodifiableList(requireNonNull(partitionListeners));
    this.diskSpaceUsageListeners = diskSpaceUsageListeners;
  }

  @Override
  public Collection<? extends PartitionListener> getPartitionListeners() {
    return partitionListeners;
  }

  @Override
  public ClusterServicesImpl getClusterServices() {
    return clusterServices;
  }

  @Override
  public CommandApiService getCommandApiService() {
    return commandApiService;
  }

  @Override
  public List<DiskSpaceUsageListener> getDiskSpaceUsageListeners() {
    return diskSpaceUsageListeners;
  }

  @Override
  public EmbeddedGatewayService getEmbeddedGatewayService() {
    return embeddedGatewayService;
  }
}
