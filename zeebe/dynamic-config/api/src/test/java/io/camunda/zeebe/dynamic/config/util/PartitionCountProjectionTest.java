/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class PartitionCountProjectionTest {

  private static final MemberId MEMBER_0 = MemberId.from("0");
  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final String GROUP_A = "a";
  private static final String GROUP_B = "b";

  @Test
  void shouldCountJoinBeforeLeaveOfSameMember() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of());
    final var phases = List.<Phase>of(sequential(GROUP_A, join(MEMBER_0, 3), leave(MEMBER_0, 1)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 1));
  }

  @Test
  void shouldNotCountJoinAfterLeaveOfSameMember() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of());
    final var phases = List.<Phase>of(sequential(GROUP_A, leave(MEMBER_0, 1), join(MEMBER_0, 3)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).isEmpty();
  }

  @Test
  void shouldNotCreditLeaveThatMayRunAfterJoin() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of());
    final var concurrent = OperationGraph.builder();
    concurrent.add(leave(MEMBER_0, 1));
    concurrent.add(join(MEMBER_0, 3));
    final var phases = List.<Phase>of(new PartitionGroupPhase(Map.of(GROUP_A, concurrent.build())));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 1));
  }

  @Test
  void shouldCountOnlyReplicasThatConcurrentOperationsStart() {
    // given
    final var configuration =
        configuration(
            Map.of(MEMBER_0, Map.of(1, State.ACTIVE), MEMBER_1, Map.of(1, State.JOINING)),
            Map.of());
    final var concurrent = OperationGraph.builder();
    concurrent.add(demote(MEMBER_0, 1));
    concurrent.add(new PartitionForceReconfigureOperation(MEMBER_0, 1, Set.of(MEMBER_0)));
    concurrent.add(join(MEMBER_0, 3));
    concurrent.add(join(MEMBER_1, 2));
    final var phases = List.<Phase>of(new PartitionGroupPhase(Map.of(GROUP_A, concurrent.build())));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyInAnyOrderEntriesOf(Map.of(MEMBER_0, 1, MEMBER_1, 1));
  }

  @Test
  void shouldCountPartitionsStartedOnBootAcrossGroups() {
    // given
    final var configuration =
        configuration(
            Map.of(MEMBER_0, Map.of(1, State.ACTIVE, 2, State.JOINING)),
            Map.of(MEMBER_0, Map.of(1, State.LEARNER), MEMBER_1, Map.of(1, State.LEAVING)));

    // when
    final var partitions = PartitionCountProjection.partitionsPerMember(configuration);

    // then
    assertThat(partitions).containsExactlyInAnyOrderEntriesOf(Map.of(MEMBER_0, 2, MEMBER_1, 1));
  }

  @Test
  void shouldAddUpIncreasesOfGroupsInSamePhase() {
    // given
    final var configuration =
        configuration(
            Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of(MEMBER_0, Map.of(1, State.ACTIVE)));
    final var phases =
        List.<Phase>of(
            PartitionGroupPhase.sequential(
                Map.of(
                    GROUP_A,
                    List.<PartitionGroupOperation>of(join(MEMBER_0, 3), leave(MEMBER_0, 1)),
                    GROUP_B,
                    List.<PartitionGroupOperation>of(join(MEMBER_0, 3), leave(MEMBER_0, 1)))));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 2));
  }

  @Test
  void shouldNotCreditLeaveOfConcurrentGroup() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of());
    final var phases =
        List.<Phase>of(
            PartitionGroupPhase.sequential(
                Map.of(
                    GROUP_A,
                    List.<PartitionGroupOperation>of(leave(MEMBER_0, 1)),
                    GROUP_B,
                    List.<PartitionGroupOperation>of(join(MEMBER_0, 1)))));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 1));
  }

  @Test
  void shouldCarryNetChangeIntoLaterPhases() {
    // given
    final var configuration = configuration(Map.of(), Map.of(MEMBER_0, Map.of(1, State.ACTIVE)));
    final var phases =
        List.<Phase>of(
            PartitionGroupPhase.sequential(
                Map.of(
                    GROUP_A,
                    List.<PartitionGroupOperation>of(join(MEMBER_1, 3)),
                    GROUP_B,
                    List.<PartitionGroupOperation>of(leave(MEMBER_0, 1)))),
            sequential(GROUP_A, join(MEMBER_0, 3), join(MEMBER_0, 4), join(MEMBER_1, 4)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyInAnyOrderEntriesOf(Map.of(MEMBER_0, 1, MEMBER_1, 2));
  }

  @Test
  void shouldCountDemotionOfJoiningReplica() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(2, State.JOINING)), Map.of());
    final var phases = List.<Phase>of(sequential(GROUP_A, demote(MEMBER_0, 2), leave(MEMBER_0, 2)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 1));
  }

  @Test
  void shouldCountLeaveOfJoiningReplica() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(2, State.JOINING)), Map.of());
    final var phases = List.<Phase>of(sequential(GROUP_A, leave(MEMBER_0, 2)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 1));
  }

  @Test
  void shouldNotCountPromotionOfLearner() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.LEARNER)), Map.of());
    final var phases = List.<Phase>of(sequential(GROUP_A, promote(MEMBER_0, 1)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).isEmpty();
  }

  @Test
  void shouldCreditReplicasRemovedByForceReconfiguration() {
    // given
    final var configuration =
        configuration(
            Map.of(
                MEMBER_0,
                Map.of(1, State.ACTIVE),
                MEMBER_1,
                Map.of(1, State.ACTIVE),
                MEMBER_2,
                Map.of(1, State.ACTIVE, 2, State.ACTIVE)),
            Map.of());
    final var phases =
        List.<Phase>of(
            sequential(
                GROUP_A,
                new PartitionForceReconfigureOperation(MEMBER_0, 1, Set.of(MEMBER_0)),
                join(MEMBER_1, 3),
                join(MEMBER_1, 4),
                join(MEMBER_2, 3),
                join(MEMBER_2, 4),
                join(MEMBER_0, 2)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase)
        .containsExactlyInAnyOrderEntriesOf(Map.of(MEMBER_0, 1, MEMBER_1, 1, MEMBER_2, 1));
  }

  @Test
  void shouldCreditReplicasOfRemovedPhysicalTenant() {
    // given
    final var configuration = configuration(Map.of(MEMBER_0, Map.of(1, State.ACTIVE)), Map.of());
    final var phases =
        List.<Phase>of(
            sequential(GROUP_A, join(MEMBER_0, 2)),
            sequential(GROUP_B, join(MEMBER_0, 3)),
            sequential(GROUP_A, new RemovePhysicalTenantOperation(MEMBER_0)),
            sequential(GROUP_B, join(MEMBER_0, 4), join(MEMBER_0, 5), join(MEMBER_0, 6)));

    // when
    final var increase = PartitionCountProjection.peakIncrease(configuration, phases);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 3));
  }

  @Test
  void shouldCountOnlyOutstandingOperationsOfPendingChange() {
    // given
    final var pending =
        configuration(Map.of(), Map.of())
            .initPlan(
                List.of(
                    sequential(GROUP_A, join(MEMBER_0, 3), join(MEMBER_0, 4)),
                    sequential(GROUP_B, join(MEMBER_1, 3))));
    final var firstOperation =
        pending.partitionGroup(GROUP_A).pendingChanges().orElseThrow().operations().firstKey();
    final var partlyApplied =
        pending.updatePartitionGroupConfig(
            GROUP_A, group -> group.completeOperation(firstOperation, UnaryOperator.identity()));

    // when
    final var increase = PartitionCountProjection.pendingPeakIncrease(partlyApplied);

    // then
    assertThat(increase).containsExactlyInAnyOrderEntriesOf(Map.of(MEMBER_0, 1, MEMBER_1, 1));
  }

  @Test
  void shouldCountChangeStartedDirectlyOnGroup() {
    // given
    final var pending =
        configuration(Map.of(), Map.of())
            .initPlan(List.of(sequential(GROUP_A, join(MEMBER_0, 3))))
            .updatePartitionGroupConfig(
                GROUP_B,
                group ->
                    group.startGraphConfigurationChange(
                        OperationGraph.sequential(List.of(join(MEMBER_0, 1), join(MEMBER_0, 2)))));

    // when
    final var increase = PartitionCountProjection.pendingPeakIncrease(pending);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 3));
  }

  @Test
  void shouldAddUpIncreasesOfPendingChanges() {
    // given
    final var pending =
        configuration(Map.of(), Map.of())
            .initPlan(List.of(sequential(GROUP_A, join(MEMBER_0, 3))))
            .initPlan(List.of(sequential(GROUP_B, join(MEMBER_0, 3))));

    // when
    final var increase = PartitionCountProjection.pendingPeakIncrease(pending);

    // then
    assertThat(increase).containsExactlyEntriesOf(Map.of(MEMBER_0, 2));
  }

  private static PartitionGroupPhase sequential(
      final String groupId, final PartitionGroupOperation... operations) {
    return PartitionGroupPhase.sequential(Map.of(groupId, List.of(operations)));
  }

  private static PartitionJoinOperation join(final MemberId memberId, final int partitionId) {
    return new PartitionJoinOperation(memberId, partitionId, 1, true);
  }

  private static PartitionLeaveOperation leave(final MemberId memberId, final int partitionId) {
    return new PartitionLeaveOperation(memberId, partitionId, 1);
  }

  private static PartitionDemoteOperation demote(final MemberId memberId, final int partitionId) {
    return new PartitionDemoteOperation(memberId, partitionId);
  }

  private static PartitionPromoteOperation promote(final MemberId memberId, final int partitionId) {
    return new PartitionPromoteOperation(memberId, partitionId);
  }

  private static CurrentClusterConfiguration configuration(
      final Map<MemberId, Map<Integer, State>> groupA,
      final Map<MemberId, Map<Integer, State>> groupB) {
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.uninitialized(),
        Map.of(GROUP_A, group(groupA), GROUP_B, group(groupB)),
        PhasedChangeState.empty());
  }

  private static PartitionGroupConfiguration group(
      final Map<MemberId, Map<Integer, State>> replicas) {
    final var members =
        replicas.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    member ->
                        BrokerPartitionState.initialize(
                            member.getValue().entrySet().stream()
                                .collect(
                                    Collectors.toMap(
                                        Map.Entry::getKey,
                                        partition ->
                                            new PartitionState(
                                                partition.getValue(),
                                                1,
                                                DynamicPartitionConfig.init()))))));
    return new PartitionGroupConfiguration(
        PartitionGroupConfiguration.INITIAL_VERSION,
        0,
        members,
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
