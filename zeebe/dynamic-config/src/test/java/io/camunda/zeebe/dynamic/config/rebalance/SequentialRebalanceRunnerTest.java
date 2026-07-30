/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.rebalance;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SequentialRebalanceRunnerTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final Map<Integer, MemberId> leaders = new HashMap<>();
  private final PartitionLeaders partitionLeaders =
      (physicalTenantId, partitionId) ->
          GROUP.equals(physicalTenantId)
              ? Optional.ofNullable(leaders.get(partitionId))
              : Optional.empty();

  @Test
  void shouldPlanATransferTowardsTheHighestPriorityReplica() {
    // given
    leaders.put(1, MEMBER_1);

    // when
    final var rebalance = run(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partitions())
        .containsExactly(
            new PartitionRebalance(GROUP, 1, MEMBER_1, MEMBER_2, PartitionRebalanceState.PENDING));
  }

  @Test
  void shouldPlanNoTransferForAPartitionAlreadyLedByItsDesiredLeader() {
    // given
    leaders.put(1, MEMBER_2);

    // when
    final var rebalance = run(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.SKIPPED);
  }

  @Test
  void shouldReportWhyAPartitionWasLeftAlone() {
    // given
    leaders.put(1, MEMBER_2);

    // when
    final var rebalance = run(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).reason()).contains("already with the desired leader");
  }

  @Test
  void shouldPlanATransferForAPartitionWithNoLeaderAtAll() {
    // given
    // no leader is recorded for partition 1

    // when
    final var rebalance = run(configurationWithPriorities(Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).currentLeader()).isNull();
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.PENDING);
  }

  @Test
  void shouldSkipAPartitionNoMemberIsEligibleToLead() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, MemberState.uninitialized().addPartition(1, active(1)));

    // when
    final var rebalance = run(configuration);

    // then
    assertThat(rebalance.partition(0).desiredLeader()).isNull();
    assertThat(rebalance.partition(0).state()).isEqualTo(PartitionRebalanceState.SKIPPED);
  }

  @Test
  void shouldPlanEveryPartitionOfTheConfigurationInOrder() {
    // given
    final var configuration =
        ClusterConfiguration.init()
            .addMember(
                MEMBER_1,
                MemberState.initializeAsActive(
                    Map.of(
                        3, active(1),
                        1, active(1),
                        2, active(1))));

    // when
    final var rebalance = run(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::partitionId)
        .containsExactly(1, 2, 3);
  }

  private RebalanceRun run(final ClusterConfiguration configuration) {
    final var rebalance = new RebalanceRun(7, RebalanceOverrides.none(), false, configuration);
    new SequentialRebalanceRunner(executor, partitionLeaders).run(rebalance).join();
    return rebalance;
  }

  private PartitionState active(final int priority) {
    return PartitionState.active(priority, partitionConfig);
  }

  private ClusterConfiguration configurationWithPriorities(
      final Map<MemberId, Integer> prioritiesForPartitionOne) {
    var configuration = ClusterConfiguration.init();
    for (final var entry : prioritiesForPartitionOne.entrySet()) {
      configuration =
          configuration.addMember(
              entry.getKey(), MemberState.initializeAsActive(Map.of(1, active(entry.getValue()))));
    }
    return configuration;
  }
}
