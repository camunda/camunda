/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupChangePlan;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupChangePlan.ClusterMembershipPhase;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ProtoBufSerializerPartitionGroupTest {

  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  private static final MemberId MEMBER_1 = MemberId.from("1");
  private static final MemberId MEMBER_2 = MemberId.from("2");
  private static final DynamicPartitionConfig EMPTY_CONFIG = DynamicPartitionConfig.init();

  private static MemberState activeMember() {
    return MemberState.initializeAsActive(Map.of());
  }

  private static MemberState activeWithPartition(final int partitionId) {
    return MemberState.initializeAsActive(
        Map.of(partitionId, PartitionState.active(1, EMPTY_CONFIG)));
  }

  @Test
  void shouldRoundTripPartitionGroupClusterConfiguration() {
    // given
    final var membership =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, activeMember())
            .addMember(MEMBER_2, activeMember());
    final var defaultGroup =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, activeWithPartition(1))
            .addMember(MEMBER_2, activeWithPartition(1));
    final var tenantAGroup =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, activeWithPartition(2))
            .addMember(MEMBER_2, activeWithPartition(2));
    final var config =
        new PartitionGroupClusterConfiguration(
            membership, Map.of("default", defaultGroup, "tenantA", tenantAGroup), Optional.empty());

    // when
    final var encoded = serializer.encodePartitionGroupClusterConfiguration(config);
    final var decoded = serializer.decodePartitionGroupClusterConfiguration(encoded);

    // then
    assertThat(decoded.clusterMembership().members()).containsKey(MEMBER_1);
    assertThat(decoded.clusterMembership().members()).containsKey(MEMBER_2);
    assertThat(decoded.partitionGroupConfigs()).containsKeys("default", "tenantA");
    assertThat(decoded.partitionGroupConfigs().get("default").members()).containsKey(MEMBER_1);
    assertThat(decoded.partitionGroupConfigs().get("tenantA").members()).containsKey(MEMBER_2);
    assertThat(decoded.pendingPlan()).isEmpty();
  }

  @Test
  void shouldMigrateOldFormatToNewFormat() {
    // given — encode using old encoder (ClusterConfiguration only)
    final var oldConfig =
        ClusterConfiguration.init()
            .addMember(MEMBER_1, activeWithPartition(1))
            .addMember(MEMBER_2, activeWithPartition(2));
    final var encoded = serializer.encode(oldConfig);

    // when — decode with new migration path
    final var migrated = serializer.decodePartitionGroupClusterConfiguration(encoded);

    // then
    assertThat(migrated.partitionGroupConfigs()).containsKey("default");
    assertThat(migrated.partitionGroupConfigs().get("default").members())
        .containsKey(MEMBER_1)
        .containsKey(MEMBER_2);
    // clusterMembership members have empty partitions
    assertThat(migrated.clusterMembership().members()).containsKey(MEMBER_1);
    assertThat(migrated.clusterMembership().members().get(MEMBER_1).partitions()).isEmpty();
    assertThat(migrated.clusterMembership().members()).containsKey(MEMBER_2);
    assertThat(migrated.clusterMembership().members().get(MEMBER_2).partitions()).isEmpty();
    assertThat(migrated.pendingPlan()).isEmpty();
  }

  @Test
  void shouldRoundTripPartitionGroupChangePlan() {
    // given — plan with membership phase and partition group parallel phase
    final var membershipPhase =
        new ClusterMembershipPhase(List.of(new MemberJoinOperation(MEMBER_2)));
    final var parallelPhase =
        new PartitionGroupParallelPhase(
            Map.of("tenantA", List.of(new PartitionJoinOperation(MEMBER_2, 3, 1))));
    final var plan = PartitionGroupChangePlan.init(42L, List.of(membershipPhase, parallelPhase));

    final var membership = ClusterConfiguration.init().addMember(MEMBER_1, activeMember());
    final var tenantAGroup =
        ClusterConfiguration.init().addMember(MEMBER_1, activeWithPartition(2));
    final var config =
        new PartitionGroupClusterConfiguration(
            membership, Map.of("tenantA", tenantAGroup), Optional.of(plan));

    // when
    final var encoded = serializer.encodePartitionGroupClusterConfiguration(config);
    final var decoded = serializer.decodePartitionGroupClusterConfiguration(encoded);

    // then
    assertThat(decoded.pendingPlan()).isPresent();
    final var decodedPlan = decoded.pendingPlan().get();
    assertThat(decodedPlan.id()).isEqualTo(42L);
    assertThat(decodedPlan.phases()).hasSize(2);
    assertThat(decodedPlan.currentPhaseIndex()).isEqualTo(0);

    // Phase 0: ClusterMembershipPhase with one operation
    assertThat(decodedPlan.phases().get(0)).isInstanceOf(ClusterMembershipPhase.class);
    final var decodedMembershipPhase = (ClusterMembershipPhase) decodedPlan.phases().get(0);
    assertThat(decodedMembershipPhase.operations()).hasSize(1);
    assertThat(decodedMembershipPhase.operations().get(0)).isInstanceOf(MemberJoinOperation.class);

    // Phase 1: PartitionGroupParallelPhase with operations for "tenantA"
    assertThat(decodedPlan.phases().get(1)).isInstanceOf(PartitionGroupParallelPhase.class);
    final var decodedParallelPhase = (PartitionGroupParallelPhase) decodedPlan.phases().get(1);
    assertThat(decodedParallelPhase.operationsPerGroup()).containsKey("tenantA");
    assertThat(decodedParallelPhase.operationsPerGroup().get("tenantA")).hasSize(1);
    assertThat(decodedParallelPhase.operationsPerGroup().get("tenantA").get(0))
        .isInstanceOf(PartitionJoinOperation.class);
  }
}
