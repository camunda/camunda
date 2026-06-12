/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupChangePlan.ClusterMembershipPhase;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupChangePlan.PartitionGroupParallelPhase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PartitionGroupClusterConfigurationTest {

  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final DynamicPartitionConfig EMPTY_PARTITION_CONFIG =
      DynamicPartitionConfig.init();

  private static ClusterConfiguration clusterConfigWithMember(
      final MemberId memberId, final MemberState memberState) {
    return ClusterConfiguration.init().addMember(memberId, memberState);
  }

  private static MemberState activeWithPartition(final int partitionId) {
    return MemberState.initializeAsActive(
        Map.of(partitionId, PartitionState.active(1, EMPTY_PARTITION_CONFIG)));
  }

  private static PartitionGroupClusterConfiguration emptyTwoGroupConfig() {
    final var membership = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var defaultGroup =
        ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(1));
    final var tenantAGroup =
        ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(2));
    return new PartitionGroupClusterConfiguration(
        membership, Map.of("default", defaultGroup, "tenantA", tenantAGroup), Optional.empty());
  }

  private static MemberState activeMember() {
    return MemberState.initializeAsActive(Map.of());
  }

  @Test
  void shouldMergeMembershipFromBothSides() {
    // given
    final var m1State = MemberState.initializeAsActive(Map.of());
    final var m2State = MemberState.initializeAsActive(Map.of());
    final var membership1 = ClusterConfiguration.init().addMember(MEMBER_1, m1State);
    final var membership2 = ClusterConfiguration.init().addMember(MEMBER_2, m2State);
    final var config1 =
        new PartitionGroupClusterConfiguration(membership1, Map.of(), Optional.empty());
    final var config2 =
        new PartitionGroupClusterConfiguration(membership2, Map.of(), Optional.empty());

    // when
    final var merged = config1.merge(config2);

    // then
    assertThat(merged.clusterMembership().members()).containsKey(MEMBER_1);
    assertThat(merged.clusterMembership().members()).containsKey(MEMBER_2);
  }

  @Test
  void shouldAdoptGroupPresentOnlyInOther() {
    // given
    final var groupConfig = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var config1 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.empty());
    final var config2 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of("tenantB", groupConfig), Optional.empty());

    // when
    final var merged = config1.merge(config2);

    // then
    assertThat(merged.partitionGroupConfigs()).containsKey("tenantB");
  }

  @Test
  void shouldAdoptGroupPresentOnlyInThis() {
    // given
    final var groupConfig = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var config1 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of("myGroup", groupConfig), Optional.empty());
    final var config2 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.empty());

    // when
    final var merged = config1.merge(config2);

    // then
    assertThat(merged.partitionGroupConfigs()).containsKey("myGroup");
  }

  @Test
  void shouldMergeOverlappingGroups() {
    // given — same group "default", config2 has higher version because it has extra member
    final var groupV1 = ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(1));
    // Simulate a higher-version config by doing a configuration change + advance
    final var groupV2 =
        ClusterConfiguration.builder()
            .version(groupV1.version() + 1)
            .members(Map.of(MEMBER_1, activeWithPartition(1), MEMBER_2, activeWithPartition(2)))
            .build();
    final var config1 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of("default", groupV1), Optional.empty());
    final var config2 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of("default", groupV2), Optional.empty());

    // when
    final var merged = config1.merge(config2);

    // then — higher version wins
    assertThat(merged.partitionGroupConfigs().get("default").version())
        .isEqualTo(groupV2.version());
    assertThat(merged.partitionGroupConfigs().get("default").members()).containsKey(MEMBER_2);
  }

  @Test
  void shouldAdoptPendingPlanFromOtherWhenAbsentLocally() {
    // given
    final var plan =
        PartitionGroupChangePlan.init(1L, List.of(new ClusterMembershipPhase(List.of())));
    final var config1 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.empty());
    final var config2 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.of(plan));

    // when
    final var merged = config1.merge(config2);

    // then
    assertThat(merged.pendingPlan()).isPresent();
    assertThat(merged.pendingPlan().get().id()).isEqualTo(1L);
  }

  @Test
  void shouldMergePendingPlansWhenBothPresent() {
    // given — plan with id=1 at phase 1 vs plan with id=1 at phase 0 → phase 1 wins
    final var phases =
        List.<PartitionGroupChangePlan.Phase>of(
            new ClusterMembershipPhase(List.of()), new ClusterMembershipPhase(List.of()));
    final var planAtPhase1 =
        new PartitionGroupChangePlan(1L, java.time.Instant.now(), 1, phases, null);
    final var planAtPhase0 =
        new PartitionGroupChangePlan(1L, java.time.Instant.now(), 0, phases, null);
    final var config1 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.of(planAtPhase1));
    final var config2 =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.of(planAtPhase0));

    // when
    final var merged = config1.merge(config2);

    // then
    assertThat(merged.pendingPlan().get().currentPhaseIndex()).isEqualTo(1);
  }

  @Test
  void shouldActivateMembershipPhaseOnInitPlan() {
    // given
    final var membership = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var config =
        new PartitionGroupClusterConfiguration(membership, Map.of(), Optional.empty());
    final var joinOp = new MemberJoinOperation(MEMBER_2);
    final var plan =
        PartitionGroupChangePlan.init(1L, List.of(new ClusterMembershipPhase(List.of(joinOp))));

    // when
    final var result = config.initPlan(plan);

    // then
    assertThat(result.pendingPlan()).isPresent();
    assertThat(result.clusterMembership().hasPendingChanges()).isTrue();
  }

  @Test
  void shouldActivatePartitionGroupParallelPhaseOnInitPlan() {
    // given
    final var membership = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var defaultGroup =
        ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(1));
    final var config =
        new PartitionGroupClusterConfiguration(
            membership, Map.of("default", defaultGroup), Optional.empty());
    final var joinOp = new PartitionJoinOperation(MEMBER_1, 2, 1);
    final var plan =
        PartitionGroupChangePlan.init(
            1L, List.of(new PartitionGroupParallelPhase(Map.of("default", List.of(joinOp)))));

    // when
    final var result = config.initPlan(plan);

    // then
    assertThat(result.pendingPlan()).isPresent();
    assertThat(result.partitionGroupConfigs().get("default").hasPendingChanges()).isTrue();
    // clusterMembership is not touched
    assertThat(result.clusterMembership().hasPendingChanges()).isFalse();
  }

  @Test
  void shouldAdvanceToNextPhase() {
    // given — two-phase plan: membership phase then partition group phase
    final var membership = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var defaultGroup =
        ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(1));
    final var config =
        new PartitionGroupClusterConfiguration(
            membership, Map.of("default", defaultGroup), Optional.empty());

    final var phase0 = new ClusterMembershipPhase(List.of());
    final var phase1 = new PartitionGroupParallelPhase(Map.of());
    final var plan = PartitionGroupChangePlan.init(1L, List.of(phase0, phase1));

    // when
    final var afterPhase0 = config.initPlan(plan);
    final var afterPhase1 = afterPhase0.activateNextPhase();

    // then
    assertThat(afterPhase1.pendingPlan().get().currentPhaseIndex()).isEqualTo(1);
  }

  @Test
  void shouldCompletePlan() {
    // given
    final var plan =
        PartitionGroupChangePlan.init(1L, List.of(new ClusterMembershipPhase(List.of())));
    final var config =
        new PartitionGroupClusterConfiguration(
            ClusterConfiguration.init(), Map.of(), Optional.of(plan));

    // when
    final var completed = config.completePlan();

    // then
    assertThat(completed.pendingPlan()).isEmpty();
  }

  @Test
  void shouldNotAffectClusterMembershipOnPartitionGroupUpdate() {
    // given
    final var config = emptyTwoGroupConfig();
    final var originalMembership = config.clusterMembership();

    // when
    final var updated =
        config.updatePartitionGroupConfig(
            "default", groupConfig -> groupConfig.addMember(MEMBER_2, activeWithPartition(3)));

    // then
    assertThat(updated.clusterMembership()).isEqualTo(originalMembership);
  }

  @Test
  void shouldNotAffectPartitionGroupsOnMembershipUpdate() {
    // given
    final var config = emptyTwoGroupConfig();
    final var originalGroups = config.partitionGroupConfigs();

    // when
    final var updated =
        config.updateClusterMembership(
            membership -> membership.addMember(MEMBER_2, activeMember()));

    // then
    assertThat(updated.partitionGroupConfigs()).isEqualTo(originalGroups);
  }
}
