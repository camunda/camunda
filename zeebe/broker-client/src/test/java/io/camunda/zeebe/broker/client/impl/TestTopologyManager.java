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
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TestTopologyManager implements BrokerTopologyManager {
  private final TestBrokerClusterState topology;
  private ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();

  TestTopologyManager() {
    topology = new TestBrokerClusterState();
  }

  TestTopologyManager(final TestBrokerClusterState topology) {
    this.topology = topology;
  }

  TestTopologyManager addPartition(final int id, final int leaderId) {
    topology.addBrokerIfAbsent(leaderId);
    topology.setPartitionLeader(id, leaderId);
    topology.addPartitionIfAbsent(id);
    return this;
  }

  TestTopologyManager addPartitionWithoutLeader(final int id) {
    topology.addPartitionIfAbsent(id);
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

  private static final class TestBrokerClusterState implements BrokerClusterState {

    private final List<String> brokers = new ArrayList<>();
    private final Map<Integer, String> partitionLeaders = new HashMap<>();
    private final List<Integer> partitions = new ArrayList<>();

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public int getClusterSize() {
      return brokers.size();
    }

    @Override
    public int getPartitionsCount() {
      return partitions.size();
    }

    @Override
    public int getReplicationFactor() {
      return 0;
    }

    @Override
    public String getLeaderForPartition(final int partition) {
      return partitionLeaders.get(partition);
    }

    @Override
    public Set<String> getFollowersForPartition(final int partition) {
      return Set.of();
    }

    @Override
    public Set<String> getInactiveNodesForPartition(final int partition) {
      return Set.of();
    }

    @Override
    public String getRandomBroker() {
      return brokers.isEmpty() ? null : brokers.getFirst();
    }

    @Override
    public List<Integer> getPartitions() {
      return partitions;
    }

    @Override
    public List<String> getBrokers() {
      return brokers;
    }

    @Override
    public String getBrokerAddress(final String memberId) {
      return "";
    }

    @Override
    public String getBrokerVersion(final String memberId) {
      return "";
    }

    @Override
    public String getBrokerRegion(final String memberId) {
      return null;
    }

    @Override
    public PartitionHealthStatus getPartitionHealth(final String memberId, final int partition) {
      return null;
    }

    @Override
    public long getLastCompletedChangeId() {
      return 0;
    }

    @Override
    public String getClusterId() {
      return "";
    }

    public void addBrokerIfAbsent(final int leaderId) {
      brokers.add(Integer.toString(leaderId));
    }

    public void setPartitionLeader(final int id, final int leaderId) {
      partitionLeaders.put(id, Integer.toString(leaderId));
    }

    public void addPartitionIfAbsent(final int id) {
      partitions.add(id);
    }
  }
}
