/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class UpdatePartitionDistributionTransformerTest {

  private static final DynamicPartitionConfig PARTITION_CONFIG = DynamicPartitionConfig.init();

  // 3 partitions, 2 zones: zone-a (1 replica, priority 1000), zone-b (1 replica, priority 500)
  // Members: zone-a_0, zone-a_1, zone-b_0 — RF = 2
  private static final ZoneAwareConfig INITIAL_CONFIG =
      new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1000), new ZoneSpec("zone-b", 1, 500)));

  // Coordinator = lexicographically smallest member ID = zone-a_0
  private static final MemberId ZONE_A_0 = MemberId.from("zone-a", 0);
  private static final MemberId ZONE_A_1 = MemberId.from("zone-a", 1);
  private static final MemberId ZONE_B_0 = MemberId.from("zone-b", 0);
  private static final MemberId COORDINATOR = ZONE_A_0;

  private static final Set<MemberId> MEMBERS = Set.of(ZONE_A_0, ZONE_A_1, ZONE_B_0);

  private static final List<PartitionId> PARTITION_IDS =
      IntStream.rangeClosed(1, 3).mapToObj(i -> new PartitionId("temp", i)).toList();

  /** Builds a topology whose partitions are placed by ZoneAwarePartitionDistributor. */
  private static ClusterConfiguration buildTopology(
      final ZoneAwareConfig config, final Set<MemberId> members) {
    final var distribution =
        new ZoneAwarePartitionDistributor(config.zones())
            .distributePartitions(members, PARTITION_IDS, config.replicationFactor());
    final var topology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c");
    return topology.setPartitionDistributorConfig(config);
  }

  @Test
  void shouldPrependConfigOpAndEmitJoinOpsWhenRfIncreases() {
    // given: initial RF=2, new RF=3 (add second zone-a replica per partition)
    // Initial placement: P1→zone-a_0(2),zone-b_0(1) | P2→zone-a_1(2),zone-b_0(1) |
    // P3→zone-a_0(2),zone-b_0(1)
    // New placement: both zone-a brokers on each partition; existing replica promoted to priority
    // 3,
    // new replica joins at priority 2.
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));

    // when
    final var result =
        new UpdatePartitionDistributionTransformer(newConfig).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new UpdatePartitionDistributorConfigOperation(COORDINATOR, newConfig),
            new PartitionJoinOperation(ZONE_A_1, 1, 2),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 1, 3),
            new PartitionJoinOperation(ZONE_A_0, 2, 2),
            new PartitionReconfigurePriorityOperation(ZONE_A_1, 2, 3),
            new PartitionJoinOperation(ZONE_A_1, 3, 2),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 3, 3));
  }

  @Test
  void shouldPrependConfigOpAndEmitLeaveOpsWhenRfDecreases() {
    // given: initial RF=2, new RF=1 (remove zone-b, keep only zone-a)
    // The single-zone distributor reassigns zone-a replicas, causing some zone-a brokers to
    // move partitions (P2: zone-a_1→leaves, P3: zone-a_1 joins, zone-a_0 and zone-b_0 leave).
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig = new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1000)));

    // when
    final var result =
        new UpdatePartitionDistributionTransformer(newConfig).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new UpdatePartitionDistributorConfigOperation(COORDINATOR, newConfig),
            new PartitionLeaveOperation(ZONE_B_0, 1, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 1, 1),
            new PartitionLeaveOperation(ZONE_A_1, 2, 1),
            new PartitionJoinOperation(ZONE_A_1, 3, 1),
            new PartitionLeaveOperation(ZONE_B_0, 3, 1),
            new PartitionLeaveOperation(ZONE_A_0, 3, 1));
  }

  @Test
  void shouldEmitReconfigurePriorityOpsWhenOnlyPriorityChanges() {
    // given: same zones and RF, priorities swapped (zone-b becomes higher priority than zone-a)
    // zone-b replicas get promoted to priority 2, zone-a replicas demoted to priority 1.
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 1, 500), new ZoneSpec("zone-b", 1, 1000)));

    // when
    final var result =
        new UpdatePartitionDistributionTransformer(newConfig).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new UpdatePartitionDistributorConfigOperation(COORDINATOR, newConfig),
            new PartitionReconfigurePriorityOperation(ZONE_B_0, 1, 2),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 1, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_1, 2, 1),
            new PartitionReconfigurePriorityOperation(ZONE_B_0, 2, 2),
            new PartitionReconfigurePriorityOperation(ZONE_B_0, 3, 2),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 3, 1));
  }

  @Test
  void shouldRejectNonZoneAwareCluster() {
    // given: a plain round-robin cluster with plain-integer member IDs (no zone)
    final var plainMembers = Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));
    final var distribution =
        new RoundRobinPartitionDistributor().distributePartitions(plainMembers, PARTITION_IDS, 2);
    final var roundRobinTopology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c");
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));
    final var transformer = new UpdatePartitionDistributionTransformer(newConfig);

    // when
    final var result = transformer.operations(roundRobinTopology);

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  @Test
  void shouldRejectRoundRobinConfigBody() {
    // given: zone-aware cluster but requesting a ROUND_ROBIN config
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var transformer =
        new UpdatePartitionDistributionTransformer(
            new PartitionDistributorConfig.RoundRobinConfig());

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  @Test
  void shouldRejectFixedConfigBody() {
    // given: zone-aware cluster but requesting a FIXED config
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var transformer =
        new UpdatePartitionDistributionTransformer(new PartitionDistributorConfig.FixedConfig());

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }
}
