/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static io.camunda.zeebe.broker.client.BrokerMemberIds.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.protocol.Protocol;
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
    final var partitionId =
        dispatchStrategy.determinePartition(topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME);

    // then - the null value will be used as fallback by the request manager to redirect to the
    // deployment partition
    assertThat(partitionId).isEqualTo(BrokerClusterState.PARTITION_ID_NULL);
  }

  @Test
  void shouldSkipPartitionsWithoutLeaders() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager.addPartition(1, null).addPartition(2, ZERO).addPartition(3, ZERO);

    // when - then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
  }

  @Test
  void shouldIterateOverPartitionsFromClusterConfiguration() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, ZERO)
        .addPartition(2, ONE)
        .addPartition(3, TWO)
        .withClusterConfiguration(
            ClusterConfiguration.builder()
                .version(1)
                .routingState(
                    Optional.of(
                        new RoutingState(
                            1, new AllPartitions(2), new MessageCorrelation.HashMod(2))))
                .build());

    // when - then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
  }

  @Test
  void shouldIterateOverNonContiguousActivePartitions() {
    // given
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, ZERO)
        .addPartition(2, ONE)
        .addPartition(3, TWO)
        .withClusterConfiguration(
            ClusterConfiguration.builder()
                .version(1)
                .routingState(
                    Optional.of(
                        new RoutingState(
                            1,
                            new ActivePartitions(1, Set.of(3), Set.of()),
                            new MessageCorrelation.HashMod(3))))
                .build());

    // when - then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
  }

  @Test
  void shouldUpdateFromClusterConfiguration() {
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager.addPartition(1, ZERO).addPartition(2, ONE).addPartition(3, TWO);

    // when -- starting with routing state version 1, with active partitions 1 and 3
    topologyManager.withClusterConfiguration(
        ClusterConfiguration.builder()
            .version(1)
            .routingState(
                Optional.of(
                    new RoutingState(
                        1,
                        new ActivePartitions(1, Set.of(3), Set.of()),
                        new MessageCorrelation.HashMod(1))))
            .build());

    // then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);

    // when -- updating to routing state version 2, with active partitions 1, 2 and 3
    topologyManager.withClusterConfiguration(
        ClusterConfiguration.builder()
            .version(1)
            .routingState(
                Optional.of(
                    new RoutingState(2, new AllPartitions(3), new MessageCorrelation.HashMod(1))))
            .build());

    // then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
  }

  @Test
  void shouldKeepRoundRobinStatePerPartitionGroup() {
    // given - two groups with the same three led partitions
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, ZERO)
        .addPartition(2, ONE)
        .addPartition(3, TWO)
        .addPartition("tenant-b", 1, ZERO)
        .addPartition("tenant-b", 2, ONE)
        .addPartition("tenant-b", 3, TWO);

    // when - interleaving requests for both groups
    // then - each group cycles independently from the initial offset
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(3);
  }

  @Test
  void shouldSkipLeaderlessPartitionsPerGroup() {
    // given - partition 1 is leaderless in the default group but has a leader in tenant-b
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, null)
        .addPartition(2, ZERO)
        .addPartition("tenant-b", 1, ZERO)
        .addPartition("tenant-b", 2, ZERO);

    // when - then - the default group skips partition 1 while tenant-b returns it
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
  }

  @Test
  void shouldNotApplyRoutingStateToNonDefaultGroups() {
    // given - routing state restricting active partitions to 1 and 3
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager
        .addPartition(1, ZERO)
        .addPartition(2, ONE)
        .addPartition(3, TWO)
        .addPartition("tenant-b", 1, ZERO)
        .addPartition("tenant-b", 2, ONE)
        .addPartition("tenant-b", 3, TWO)
        .withClusterConfiguration(
            ClusterConfiguration.builder()
                .version(1)
                .routingState(
                    Optional.of(
                        new RoutingState(
                            1,
                            new ActivePartitions(1, Set.of(3), Set.of()),
                            new MessageCorrelation.HashMod(3))))
                .build());

    // when - then - the default group cycles over the active partitions only
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, Protocol.DEFAULT_PARTITION_GROUP_NAME))
        .isEqualTo(3);

    // and tenant-b cycles over all of its partitions
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(3);
  }

  @Test
  void shouldReturnNullValueForUnknownGroup() {
    // given - only the default group is known
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager.addPartition(1, ZERO);

    // when
    final var partitionId = dispatchStrategy.determinePartition(topologyManager, "unknown");

    // then
    assertThat(partitionId).isEqualTo(BrokerClusterState.PARTITION_ID_NULL);
  }
}
