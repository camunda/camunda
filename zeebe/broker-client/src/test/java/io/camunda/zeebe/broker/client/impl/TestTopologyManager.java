/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

final class TestTopologyManager implements BrokerTopologyManager {
  private final Map<String, TestBrokerClusterState> topologies = new HashMap<>();
  private ClusterConfiguration clusterConfiguration = ClusterConfiguration.uninitialized();

  TestTopologyManager() {
    this(new TestBrokerClusterState());
  }

  TestTopologyManager(final TestBrokerClusterState topology) {
    if (topology != null) {
      topologies.put(Protocol.DEFAULT_PARTITION_GROUP_NAME, topology);
    }
  }

  TestTopologyManager addPartition(final int id, final BrokerMemberId leaderId) {
    return addPartition(Protocol.DEFAULT_PARTITION_GROUP_NAME, id, leaderId);
  }

  TestTopologyManager addPartition(
      final String partitionGroup, final int id, final BrokerMemberId leaderId) {
    final var topology =
        topologies.computeIfAbsent(partitionGroup, group -> new TestBrokerClusterState());
    if (leaderId != null) {
      topology.addBrokerIfAbsent(leaderId);
      topology.setPartitionLeader(id, leaderId);
    }

    topology.addPartitionIfAbsent(id);
    return this;
  }

  TestTopologyManager withClusterConfiguration(final ClusterConfiguration clusterConfiguration) {
    this.clusterConfiguration = clusterConfiguration;
    return this;
  }

  @Override
  public BrokerClusterState getTopology(final String physicalTenantId) {
    return topologies.get(physicalTenantId);
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

  @NullMarked
  private static final class TestBrokerClusterState implements BrokerClusterState {

    private final List<BrokerMemberId> brokers = new ArrayList<>();
    private final Map<Integer, BrokerMemberId> partitionLeaders = new HashMap<>();
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
    public BrokerMemberId getLeaderForPartition(final int partition) {
      return partitionLeaders.get(partition);
    }

    @Override
    public Set<BrokerMemberId> getFollowersForPartition(final int partition) {
      return Set.of();
    }

    @Override
    public Set<BrokerMemberId> getInactiveNodesForPartition(final int partition) {
      return Set.of();
    }

    @Override
    public @Nullable BrokerMemberId getRandomBroker() {
      return brokers.isEmpty() ? null : brokers.get(0);
    }

    @Override
    public List<Integer> getPartitions() {
      return partitions;
    }

    @Override
    public List<BrokerMemberId> getBrokers() {
      return brokers;
    }

    @Override
    public String getBrokerAddress(final BrokerMemberId brokerId) {
      return "";
    }

    @Override
    public String getBrokerVersion(final BrokerMemberId brokerId) {
      return "";
    }

    @Override
    public @Nullable PartitionHealthStatus getPartitionHealth(
        final BrokerMemberId brokerId, final int partition) {
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

    public void addBrokerIfAbsent(final BrokerMemberId leaderId) {
      brokers.add(leaderId);
    }

    public void setPartitionLeader(final int id, final BrokerMemberId leaderId) {
      partitionLeaders.put(id, leaderId);
    }

    public void addPartitionIfAbsent(final int id) {
      partitions.add(id);
    }
  }
}
