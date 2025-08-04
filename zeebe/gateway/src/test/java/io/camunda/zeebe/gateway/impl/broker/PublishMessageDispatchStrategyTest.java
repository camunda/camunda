/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.broker;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyListener;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.gateway.api.util.TestBrokerClusterState;
import io.camunda.zeebe.protocol.impl.SubscriptionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import java.util.Optional;
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
    assertThat(dispatchStrategy.determinePartition(topologyManager))
        .isEqualTo(
            SubscriptionUtil.getSubscriptionPartitionId(
                BufferUtil.wrapString(correlationKey), partitionCount));
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
    assertThat(dispatchStrategy.determinePartition(topologyManager))
        .isEqualTo(
            SubscriptionUtil.getSubscriptionPartitionId(
                BufferUtil.wrapString(correlationKey), messagePartitionCount));
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
