/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;

final class TestTopologyManager implements BrokerTopologyManager {
  private final BrokerClusterStateImpl topology;
  private ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();

  TestTopologyManager() {
    this(new BrokerClusterStateImpl());
  }

  TestTopologyManager(final BrokerClusterStateImpl topology) {
    this.topology = topology;
  }

  TestTopologyManager addPartition(final int id, final int leaderId) {
    if (leaderId != BrokerClusterState.NODE_ID_NULL) {
      topology.addBrokerIfAbsent(leaderId);
      topology.setPartitionLeader(id, leaderId, 1);
      topology.setClusterSize(topology.getBrokers().size());
    }

    topology.addPartitionIfAbsent(id);
    topology.setPartitionsCount(topology.getPartitions().size());
    return this;
  }

  TestTopologyManager withClusterConfiguration(final ClusterConfiguration clusterConfiguration) {
    this.clusterConfiguration = clusterConfiguration;
    return this;
  }

  @Override
  public BrokerClusterState getTopology() {
    return topology;
  }

  @Override
  public ClusterConfiguration getClusterConfiguration() {
    return clusterConfiguration;
  }

  @Override
  public void addTopologyListener(final BrokerTopologyListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeTopologyListener(final BrokerTopologyListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onClusterConfigurationUpdated(final ClusterConfiguration clusterConfiguration) {
    throw new UnsupportedOperationException();
  }
}
