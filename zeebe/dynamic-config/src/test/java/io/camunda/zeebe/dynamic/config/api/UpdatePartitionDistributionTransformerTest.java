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

  private static final Set<MemberId> MEMBERS =
      Set.of(MemberId.from("zone-a", 0), MemberId.from("zone-a", 1), MemberId.from("zone-b", 0));

  private static final List<PartitionId> PARTITION_IDS =
      IntStream.rangeClosed(1, 3).mapToObj(i -> PartitionId.from("temp", i)).toList();

  /** Builds a topology whose partitions are placed by ZoneAwarePartitionDistributor. */
  private static io.camunda.zeebe.dynamic.config.state.ClusterConfiguration buildTopology(
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
    // given: initial RF=2, new RF=3 (add zone-a replica)
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 2, 1000), new ZoneSpec("zone-b", 1, 500)));
    final var transformer = new UpdatePartitionDistributionTransformer(newConfig);

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    final var ops = result.get();

    // first op must set the new config
    assertThat(ops.get(0)).isInstanceOf(UpdatePartitionDistributorConfigOperation.class);
    final var configOp = (UpdatePartitionDistributorConfigOperation) ops.get(0);
    assertThat(configOp.config()).isEqualTo(newConfig);

    // remaining ops must include join operations (RF went from 2 → 3)
    final var joinOps = ops.stream().filter(PartitionJoinOperation.class::isInstance).toList();
    assertThat(joinOps).as("expected one join per partition when adding a replica").hasSize(3);
  }

  @Test
  void shouldPrependConfigOpAndEmitLeaveOpsWhenRfDecreases() {
    // given: initial RF=2, new RF=1 (remove zone-b replica)
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig = new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1000)));
    final var transformer = new UpdatePartitionDistributionTransformer(newConfig);

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    final var ops = result.get();

    assertThat(ops.get(0)).isInstanceOf(UpdatePartitionDistributorConfigOperation.class);
    // When RF decreases the distributor may still reassign replicas within zones (joins and
    // leaves), but net replica count per partition must decrease: leaves > joins.
    final long leaveCount = ops.stream().filter(PartitionLeaveOperation.class::isInstance).count();
    final long joinCount = ops.stream().filter(PartitionJoinOperation.class::isInstance).count();
    assertThat(leaveCount)
        .as("leaves must exceed joins when RF decreases")
        .isGreaterThan(joinCount);
  }

  @Test
  void shouldEmitReconfigurePriorityOpsWhenOnlyPriorityChanges() {
    // given: same zones and RF, only priority differs
    final var currentTopology = buildTopology(INITIAL_CONFIG, MEMBERS);
    final var newConfig =
        new ZoneAwareConfig(
            List.of(new ZoneSpec("zone-a", 1, 500), new ZoneSpec("zone-b", 1, 1000)));
    final var transformer = new UpdatePartitionDistributionTransformer(newConfig);

    // when
    final var result = transformer.operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    final var ops = result.get();

    assertThat(ops.get(0)).isInstanceOf(UpdatePartitionDistributorConfigOperation.class);

    final var priorityOps =
        ops.stream().filter(PartitionReconfigurePriorityOperation.class::isInstance).toList();
    assertThat(priorityOps)
        .as("expected reconfigure-priority ops for each partition replica when priority changes")
        .isNotEmpty();
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
