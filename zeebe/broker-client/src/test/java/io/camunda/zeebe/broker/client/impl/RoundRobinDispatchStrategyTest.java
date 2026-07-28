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

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import java.util.Map;
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
        dispatchStrategy.determinePartition(
            topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);

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
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
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
            getConfigurationWithRoutingState(
                new RoutingState(1, new AllPartitions(2), new MessageCorrelation.HashMod(2))));

    // when - then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(2);
  }

  private static CurrentClusterConfiguration getConfigurationWithRoutingState(
      final RoutingState value) {
    final var partitionGroup = PartitionGroupConfiguration.empty(1).setRoutingState(value);
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionGroup),
        PhasedChangeState.empty());
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
            getConfigurationWithRoutingState(
                new RoutingState(
                    1,
                    new ActivePartitions(1, Set.of(3), Set.of()),
                    new MessageCorrelation.HashMod(3))));

    // when - then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);
  }

  @Test
  void shouldUpdateFromClusterConfiguration() {
    final var dispatchStrategy = new RoundRobinDispatchStrategy();
    final var topologyManager = new TestTopologyManager();
    topologyManager.addPartition(1, ZERO).addPartition(2, ONE).addPartition(3, TWO);

    // when -- starting with routing state version 1, with active partitions 1 and 3
    topologyManager.withClusterConfiguration(
        getConfigurationWithRoutingState(
            new RoutingState(
                1,
                new ActivePartitions(1, Set.of(3), Set.of()),
                new MessageCorrelation.HashMod(1))));

    // then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);

    // when -- updating to routing state version 2, with active partitions 1, 2 and 3
    topologyManager.withClusterConfiguration(
        getConfigurationWithRoutingState(
            new RoutingState(2, new AllPartitions(3), new MessageCorrelation.HashMod(1))));

    // then
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
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
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(2);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
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
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
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
            getConfigurationWithRoutingState(
                new RoutingState(
                    1,
                    new ActivePartitions(1, Set.of(3), Set.of()),
                    new MessageCorrelation.HashMod(3))));

    // when - then - the default group cycles over the active partitions only
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);

    // and tenant-b cycles over all of its partitions
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(2);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(3);
  }

  @Test
  void shouldApplyOwnRoutingStatePerNonDefaultGroup() {
    // given - default and tenant-b each have their own routing state, restricting active
    // partitions differently
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
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                GlobalConfiguration.init(),
                Map.of(
                    PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
                    PartitionGroupConfiguration.empty(1)
                        .setRoutingState(
                            new RoutingState(
                                1,
                                new ActivePartitions(1, Set.of(3), Set.of()),
                                new MessageCorrelation.HashMod(3))),
                    "tenant-b",
                    PartitionGroupConfiguration.empty(1)
                        .setRoutingState(
                            new RoutingState(
                                1,
                                new ActivePartitions(1, Set.of(2), Set.of()),
                                new MessageCorrelation.HashMod(3)))),
                PhasedChangeState.empty()));

    // when - then - the default group cycles over partitions 1 and 3 only
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(1);
    assertThat(
            dispatchStrategy.determinePartition(
                topologyManager, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
        .isEqualTo(3);

    // and tenant-b cycles over partitions 1 and 2 only, per its own routing state
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(1);
    assertThat(dispatchStrategy.determinePartition(topologyManager, "tenant-b")).isEqualTo(2);
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
