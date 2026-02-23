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
import io.camunda.zeebe.protocol.impl.PartitionUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class HashBasedDispatchStrategyTest {

  @Test
  void shouldDispatchViaTopology() {
    // given
    final var businessId = "order-12345";
    final var dispatchStrategy = new HashBasedDispatchStrategy(businessId, "business id");
    final var partitionCount = 3;

    // when -- No routing state in cluster configuration
    final var topologyManager =
        new TestTopologyManager(
            new TestBrokerClusterState(partitionCount), ClusterConfiguration.uninitialized());

    // then - the request is dispatched based on the partition count from the topology
    assertThat(dispatchStrategy.determinePartition(topologyManager))
        .isEqualTo(PartitionUtil.getPartitionId(BufferUtil.wrapString(businessId), partitionCount));
  }

  @Test
  void shouldDispatchViaRoutingState() {
    // given
    final var businessId = "order-12345";
    final var dispatchStrategy = new HashBasedDispatchStrategy(businessId, "business id");
    final var partitionCount = 3;
    final var messagePartitionCount = 2;

    // when -- Routing state is available in cluster configuration
    final var routingState =
        new RoutingState(1, new AllPartitions(3), new HashMod(messagePartitionCount));
    final var clusterConfiguration =
        ClusterConfiguration.builder().version(1).routingState(Optional.of(routingState)).build();

    final var topologyManager =
        new TestTopologyManager(new TestBrokerClusterState(partitionCount), clusterConfiguration);

    // then - the request is dispatched based on the routing state
    assertThat(dispatchStrategy.determinePartition(topologyManager))
        .isEqualTo(
            PartitionUtil.getPartitionId(BufferUtil.wrapString(businessId), messagePartitionCount));
  }

  @Test
  void shouldAlwaysRouteToSamePartitionForSameKey() {
    // given
    final var businessId = "consistent-key";
    final var partitionCount = 5;

    final var topologyManager =
        new TestTopologyManager(
            new TestBrokerClusterState(partitionCount), ClusterConfiguration.uninitialized());

    // when - we create multiple strategies with the same key
    final var strategy1 = new HashBasedDispatchStrategy(businessId, "business id");
    final var strategy2 = new HashBasedDispatchStrategy(businessId, "business id");

    // then - both should route to the same partition
    assertThat(strategy1.determinePartition(topologyManager))
        .isEqualTo(strategy2.determinePartition(topologyManager));
  }

  @Test
  void shouldRouteSameAsPublishMessageDispatchStrategy() {
    // given - the same key used as both a correlation key and a business id
    final var key = "shared-key";
    final var partitionCount = 5;

    final var topologyManager =
        new TestTopologyManager(
            new TestBrokerClusterState(partitionCount), ClusterConfiguration.uninitialized());

    // when
    final var hashBasedStrategy = new HashBasedDispatchStrategy(key, "business id");
    final var messageStrategy = new PublishMessageDispatchStrategy(key);

    // then - both strategies should route to the same partition for the same key
    assertThat(hashBasedStrategy.determinePartition(topologyManager))
        .isEqualTo(messageStrategy.determinePartition(topologyManager));
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
