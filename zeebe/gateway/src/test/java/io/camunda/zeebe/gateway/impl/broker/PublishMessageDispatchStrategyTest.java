/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import static org.junit.jupiter.api.Assertions.*;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PublishMessageDispatchStrategyTest {

  @Test
  void shouldDispatchViaTopology() {
    // given
    final var correlationKey = "correlationKey";
    final var dispatchStrategy = new PublishMessageDispatchStrategy(correlationKey);
    final var partitionCount = 3;

    // when -- No routing state in cluster configuration
    final var topologyManager =
        new TestTopologyManager(
            new TestBrokerClusterState(partitionCount), ClusterConfiguration.uninitialized());

    // then - the request is dispatched based on the partition count from the topology
    assertEquals(
        SubscriptionUtil.getSubscriptionPartitionId(
            BufferUtil.wrapString(correlationKey), partitionCount),
        dispatchStrategy.determinePartition(topologyManager));
  }

  @Test
  void shouldDispatchViaRoutingState() {
    // given
    final var correlationKey = "correlationKey";
    final var dispatchStrategy = new PublishMessageDispatchStrategy(correlationKey);
    final var partitionCount = 3;
    final var messagePartitionCount = 2;

    // when -- Routing state is available in cluster configuration
    final var routingState =
        new RoutingState(1, new AllPartitions(3), new HashMod(messagePartitionCount));
    final var clusterConfiguration =
        new ClusterConfiguration(
            1, Map.of(), Optional.empty(), Optional.empty(), Optional.of(routingState));
    final var topologyManager =
        new TestTopologyManager(new TestBrokerClusterState(partitionCount), clusterConfiguration);

    // then - the request is dispatched based on the routing state
    assertEquals(
        SubscriptionUtil.getSubscriptionPartitionId(
            BufferUtil.wrapString(correlationKey), messagePartitionCount),
        dispatchStrategy.determinePartition(topologyManager));
  }

  private record TestBrokerClusterState(int partitionCount) implements BrokerClusterState {

    @Override
    public boolean isInitialized() {
      return true;
    }

    @Override
    public int getClusterSize() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPartitionsCount() {
      return partitionCount;
    }

    @Override
    public int getReplicationFactor() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getLeaderForPartition(final int partition) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Integer> getFollowersForPartition(final int partition) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Integer> getInactiveNodesForPartition(final int partition) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getRandomBroker() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Integer> getPartitions() {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<Integer> getBrokers() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getBrokerAddress(final int brokerId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getPartition(final int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getBrokerVersion(final int brokerId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PartitionHealthStatus getPartitionHealth(final int brokerId, final int partition) {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getLastCompletedChangeId() {
      throw new UnsupportedOperationException();
    }
  }

  private record TestTopologyManager(
      BrokerClusterState topology, ClusterConfiguration clusterConfiguration)
      implements BrokerTopologyManager {

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
}
