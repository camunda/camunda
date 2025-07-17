/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class RoundRobinDispatchStrategyTest {
  @Test
  void shouldReturnNullValueIfNoTopology() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager(null);

    // when
    final var partitionId = dispatchStrategy.determinePartition(topologyManager);

    // then - the null value will be used as fallback by the request manager to redirect to the
    // deployment partition
    assertThat(partitionId).isEqualTo(BrokerClusterState.PARTITION_ID_NULL);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, BrokerClusterState.NODE_ID_NULL)
        .addPartition(2, 0)
        .addPartition(3, 0);

    // when - then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
  }

  @Test
  void shouldIterateOverPartitionsFromClusterConfiguration() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, 0)
        .addPartition(2, 1)
        .addPartition(3, 2)
        .withClusterConfiguration(
            new ClusterConfiguration(
                1,
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                    new RoutingState(1, new AllPartitions(2), new MessageCorrelation.HashMod(2))),
                Optional.empty()));

    // when - then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(2);
  }

  @Test
  void shouldIterateOverNonContiguousActivePartitions() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, 0)
        .addPartition(2, 1)
        .addPartition(3, 2)
        .withClusterConfiguration(
            new ClusterConfiguration(
                1,
                Map.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(
                    new RoutingState(
                        1,
                        new ActivePartitions(1, Set.of(3), Set.of()),
                        new MessageCorrelation.HashMod(3))),
                Optional.empty()));

    // when - then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
  }

  @Test
  void shouldUpdateFromClusterConfiguration() {
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager.addPartition(1, 0).addPartition(2, 1).addPartition(3, 2);

    // when -- starting with routing state version 1, with active partitions 1 and 3
    topologyManager.withClusterConfiguration(
        new ClusterConfiguration(
            1,
            Map.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new RoutingState(
                    1,
                    new ActivePartitions(1, Set.of(3), Set.of()),
                    new MessageCorrelation.HashMod(1))),
            Optional.empty()));

    // then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);

    // when -- updating to routing state version 2, with active partitions 1, 2 and 3
    topologyManager.withClusterConfiguration(
        new ClusterConfiguration(
            1,
            Map.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(
                new RoutingState(2, new AllPartitions(3), new MessageCorrelation.HashMod(1))),
            Optional.empty()));

    // then
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager)).isEqualTo(3);
  }
}
