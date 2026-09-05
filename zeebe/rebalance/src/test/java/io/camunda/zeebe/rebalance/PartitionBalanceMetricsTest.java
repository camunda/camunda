/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class PartitionBalanceMetricsTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final Map<Integer, MemberId> leaders = new HashMap<>();
  private final PartitionLeaders partitionLeaders =
      physicalTenantId ->
          partitionId ->
              GROUP.equals(physicalTenantId)
                  ? Optional.ofNullable(leaders.get(partitionId))
                  : Optional.empty();
  private final PartitionBalanceMetrics metrics =
      new PartitionBalanceMetrics(registry, partitionLeaders);

  @Test
  void shouldReportAPartitionLedByItsHighestPriorityMemberAsBalanced() {
    // given
    leaders.put(1, MEMBER_2);

    // when
    metrics.onClusterConfigurationUpdated(configurationWithPriorities(Map.of(MEMBER_1, 1)));

    // then
    assertThat(balanced(1)).isEqualTo(1);
  }

  @Test
  void shouldReportAPartitionLedByAnotherMemberAsUnbalanced() {
    // given
    leaders.put(1, MEMBER_1);

    // when
    metrics.onClusterConfigurationUpdated(configurationWithPriorities(Map.of(MEMBER_1, 1)));

    // then
    assertThat(balanced(1)).isZero();
  }

  @Test
  void shouldReportAPartitionWithNoLeaderAsUnbalanced() {
    // when
    metrics.onClusterConfigurationUpdated(configurationWithPriorities(Map.of(MEMBER_1, 1)));

    // then
    assertThat(balanced(1)).isZero();
  }

  @Test
  void shouldFollowLeadershipWithoutBeingToldItMoved() {
    // given
    metrics.onClusterConfigurationUpdated(configurationWithPriorities(Map.of(MEMBER_1, 1)));
    assertThat(balanced(1)).isZero();

    // when
    leaders.put(1, MEMBER_2);

    // then
    assertThat(balanced(1)).isEqualTo(1);
  }

  @Test
  void shouldReportNothingUntilAConfigurationArrives() {
    // when
    metrics.onClusterConfigurationUpdated(ClusterConfiguration.uninitialized());

    // then
    assertThat(registry.find("zeebe.cluster.partition.balanced").gauges()).isEmpty();
  }

  @Test
  void shouldStopReportingOnAPartitionTheConfigurationNoLongerHas() {
    // given
    metrics.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MEMBER_1, MemberState.initializeAsActive(partitions(1, 2))));

    // when
    metrics.onClusterConfigurationUpdated(
        ClusterConfiguration.init()
            .addMember(MEMBER_1, MemberState.initializeAsActive(partitions(1))));

    // then
    assertThat(registry.find("zeebe.cluster.partition.balanced").tag("partition", "2").gauge())
        .isNull();
    assertThat(registry.find("zeebe.cluster.partition.balanced").tag("partition", "1").gauge())
        .isNotNull();
  }

  @Test
  void shouldStopReportingOnThePartitionsOfADisabledPhysicalTenant() {
    // given
    leaders.put(1, MEMBER_2);
    final var configuration =
        CurrentClusterConfiguration.fromLegacy(configurationWithPriorities(Map.of(MEMBER_1, 1)));
    metrics.onClusterConfigurationUpdated(configuration);

    // when
    metrics.onClusterConfigurationUpdated(
        configuration.updatePartitionGroupConfig(GROUP, PartitionGroupConfiguration::disable));

    // then
    assertThat(registry.find("zeebe.cluster.partition.balanced").gauges()).isEmpty();
  }

  private double balanced(final int partitionId) {
    return registry
        .get("zeebe.cluster.partition.balanced")
        .tag("partition", String.valueOf(partitionId))
        .tag("physicalTenant", GROUP)
        .gauge()
        .value();
  }

  private ClusterConfiguration configurationWithPriorities(
      final Map<MemberId, Integer> lowerPriorities) {
    var configuration = ClusterConfiguration.init();
    for (final var entry : lowerPriorities.entrySet()) {
      configuration =
          configuration.addMember(
              entry.getKey(),
              MemberState.initializeAsActive(
                  Map.of(
                      1, PartitionState.active(entry.getValue(), DynamicPartitionConfig.init()))));
    }
    return configuration.addMember(
        MEMBER_2,
        MemberState.initializeAsActive(
            Map.of(1, PartitionState.active(9, DynamicPartitionConfig.init()))));
  }

  private Map<Integer, PartitionState> partitions(final int... partitionIds) {
    final Map<Integer, PartitionState> partitions = new HashMap<>();
    for (final var partitionId : partitionIds) {
      partitions.put(partitionId, PartitionState.active(1, DynamicPartitionConfig.init()));
    }
    return partitions;
  }
}
