/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SequentialRebalanceRunnerTest {

  private static final String GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final Map<String, Map<Integer, MemberId>> leaders = new HashMap<>();
  private final Set<String> unavailableGroups = new HashSet<>();
  private final Map<String, Integer> groupLookups = new HashMap<>();
  private final PartitionLeaders partitionLeaders =
      physicalTenantId -> {
        groupLookups.merge(physicalTenantId, 1, Integer::sum);
        if (unavailableGroups.contains(physicalTenantId)) {
          throw new IllegalStateException(
              "Topology for physical tenant %s is unavailable".formatted(physicalTenantId));
        }
        final var groupLeaders = leaders.getOrDefault(physicalTenantId, Map.of());
        return partitionId -> Optional.ofNullable(groupLeaders.get(partitionId));
      };

  @Test
  void shouldPlanATransferTowardsTheHighestPriorityReplica() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_1);

    // when
    final var rebalance = run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partitions())
        .containsExactly(PartitionRebalance.pending(GROUP, 1, MEMBER_1, MEMBER_2));
  }

  @Test
  void shouldPlanNoTransferForAPartitionAlreadyLedByItsDesiredLeader() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>()).put(1, MEMBER_2);

    // when
    final var rebalance = run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.COMPLETED);
    assertThat(rebalance.partition(0).outcome())
        .isEqualTo(PartitionRebalanceOutcome.ALREADY_LEADER);
  }

  @Test
  void shouldPlanATransferForAPartitionWithNoLeaderAtAll() {
    // given / when
    final var rebalance = run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).currentLeader()).isNull();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.PENDING);
  }

  @Test
  void shouldPlanAPendingEntryWithNoCurrentLeaderWhenTheKnownTopologyHasNoLeaderForThePartition() {
    // given
    leaders.computeIfAbsent(GROUP, ignored -> new HashMap<>());

    // when
    final var rebalance = run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).currentLeader()).isNull();
    assertThat(rebalance.partition(0).progress()).isEqualTo(PartitionRebalanceProgress.PENDING);
  }

  @Test
  void shouldFailPlanningWhenTheGroupTopologyIsUnavailable() {
    // given
    unavailableGroups.add(GROUP);

    // when / then
    assertThatThrownBy(() -> run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldObtainTheGroupTopologyOnceForAllOfItsPartitions() {
    // given
    final var configuration =
        configurationOf(
            Map.of(
                GROUP,
                Map.of(
                    MEMBER_1,
                    Map.of(
                        1, active(1),
                        2, active(1),
                        3, active(1)))));

    // when
    run(configuration);

    // then
    assertThat(groupLookups).containsEntry(GROUP, 1);
  }

  @Test
  void shouldPlanATransferWithNoOutcomeYet() {
    // given / when
    final var rebalance = run(groupConfiguration(GROUP, Map.of(MEMBER_1, 1, MEMBER_2, 2)));

    // then
    assertThat(rebalance.partition(0).outcome()).isNull();
  }

  @Test
  void shouldPlanEveryPartitionOfTheConfigurationInOrder() {
    // given
    final var configuration =
        configurationOf(
            Map.of(
                GROUP,
                Map.of(
                    MEMBER_1,
                    Map.of(
                        3, active(1),
                        1, active(1),
                        2, active(1)))));

    // when
    final var rebalance = run(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::partitionId)
        .containsExactly(1, 2, 3);
  }

  @Test
  void shouldPlanEveryPhysicalTenantsPartitionGroupIndependently() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_2);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)), MEMBER_2, Map.of(1, active(2))),
                "tenant-b",
                    Map.of(MEMBER_1, Map.of(1, active(2)), MEMBER_2, Map.of(1, active(1)))));

    // when
    final var rebalance = run(configuration);

    // then
    assertThat(rebalance.partitions())
        .containsExactlyInAnyOrder(
            PartitionRebalance.pending("tenant-a", 1, MEMBER_1, MEMBER_2),
            PartitionRebalance.pending("tenant-b", 1, MEMBER_2, MEMBER_1));
  }

  @Test
  void shouldSortThePlanByPhysicalTenantIdThenPartitionId() {
    // given
    leaders.computeIfAbsent("tenant-a", ignored -> new HashMap<>()).put(1, MEMBER_1);
    leaders.computeIfAbsent("tenant-b", ignored -> new HashMap<>()).put(1, MEMBER_1);
    final var configuration =
        configurationOf(
            Map.of(
                "tenant-b", Map.of(MEMBER_1, Map.of(2, active(1), 1, active(1))),
                "tenant-a", Map.of(MEMBER_1, Map.of(1, active(1)))));

    // when
    final var rebalance = run(configuration);

    // then
    assertThat(rebalance.partitions())
        .map(PartitionRebalance::physicalTenantId, PartitionRebalance::partitionId)
        .containsExactly(tuple("tenant-a", 1), tuple("tenant-b", 1), tuple("tenant-b", 2));
  }

  private RebalanceRun run(final CurrentClusterConfiguration configuration) {
    final var rebalance =
        new RebalanceRun(7, RebalanceOverrides.none(), false, configuration, Instant.EPOCH);
    new SequentialRebalanceRunner(executor, partitionLeaders).run(rebalance).join();
    return rebalance;
  }

  private PartitionState active(final int priority) {
    return PartitionState.active(priority, partitionConfig);
  }

  private CurrentClusterConfiguration groupConfiguration(
      final String group, final Map<MemberId, Integer> prioritiesForPartitionOne) {
    final Map<MemberId, Map<Integer, PartitionState>> partitionsPerMember = new HashMap<>();
    prioritiesForPartitionOne.forEach(
        (memberId, priority) -> partitionsPerMember.put(memberId, Map.of(1, active(priority))));
    return configurationOf(Map.of(group, partitionsPerMember));
  }

  private CurrentClusterConfiguration configurationOf(
      final Map<String, Map<MemberId, Map<Integer, PartitionState>>> partitionsPerGroup) {
    final Map<MemberId, BrokerState> globalMembers = new HashMap<>();
    final Map<String, PartitionGroupConfiguration> groups = new HashMap<>();
    partitionsPerGroup.forEach(
        (groupId, membersInGroup) -> {
          final Map<MemberId, BrokerPartitionState> groupMembers = new HashMap<>();
          membersInGroup.forEach(
              (memberId, partitions) -> {
                globalMembers.putIfAbsent(memberId, BrokerState.initializeAsActive());
                groupMembers.put(memberId, BrokerPartitionState.initialize(partitions));
              });
          groups.put(
              groupId,
              new PartitionGroupConfiguration(
                  PartitionGroupConfiguration.INITIAL_VERSION,
                  PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
                  groupMembers,
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty()));
        });
    final var globalConfiguration =
        new GlobalConfiguration(
            GlobalConfiguration.INITIAL_VERSION,
            Optional.empty(),
            globalMembers,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        globalConfiguration,
        groups,
        PhasedChangeState.empty());
  }
}
