/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class FailbackRequestTransformerTest {

  private static final DynamicPartitionConfig PARTITION_CONFIG = DynamicPartitionConfig.init();

  // single-zone cluster left over after zone-b failed over: 2 partitions, zone-a only (RF=1)
  private static final ZoneAwareConfig SINGLE_ZONE_CONFIG =
      new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));

  private static final Set<MemberId> SINGLE_ZONE_MEMBERS = Set.of(ZONE_A_0);

  private static final List<PartitionId> PARTITION_IDS =
      IntStream.rangeClosed(1, 2).mapToObj(i -> new PartitionId("temp", i)).toList();

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
  void shouldAddBrokersAndReincludeZoneInConfig() {
    // given: zone-a only cluster; zone-b fails back with 1 replica
    final var currentTopology = buildTopology(SINGLE_ZONE_CONFIG, SINGLE_ZONE_MEMBERS);
    final var expectedConfig =
        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 500)));

    // when
    final var result =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of(ZONE_B_0))
            .operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    final var operations = result.get();
    assertThat(operations)
        .containsExactly(
            new MemberJoinOperation(ZONE_B_0),
            new UpdatePartitionDistributorConfigOperation(ZONE_A_0, expectedConfig),
            new PartitionJoinOperation(ZONE_B_0, 1, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 1, 2),
            new PartitionJoinOperation(ZONE_B_0, 2, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 2, 2));

    final var newTopology = TestTopologyChangeSimulator.apply(currentTopology, operations);
    assertThat(newTopology.partitionDistributorConfig()).hasValue(expectedConfig);
    assertThat(newTopology.members().keySet()).containsExactlyInAnyOrder(ZONE_A_0, ZONE_B_0);
  }

  @Test
  void shouldRejectFailbackWhenZoneAlreadyPresent() {
    // given: dual-zone cluster already containing zone-b
    final var dualZoneConfig =
        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 500)));
    final var currentTopology = buildTopology(dualZoneConfig, Set.of(ZONE_A_0, ZONE_B_0));

    // when
    final var result =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of(ZONE_B_1))
            .operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("already present");
  }

  @Test
  void shouldRejectFailbackWhenConfigNotZoneAware() {
    // given: a plain round-robin cluster (config present but not ZoneAware)
    final var plainMembers = Set.of(MemberId.from("0"), MemberId.from("1"));
    final var distribution =
        new RoundRobinPartitionDistributor().distributePartitions(plainMembers, PARTITION_IDS, 2);
    final var roundRobinTopology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c")
            .setPartitionDistributorConfig(new RoundRobinConfig());
    // and: the same topology without any persisted partition distributor config
    final var noConfigTopology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c");
    assertThat(noConfigTopology.partitionDistributorConfig()).isEmpty();

    // when / then: config present but not ZoneAware
    final var roundRobinResult =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of(ZONE_B_0))
            .operations(roundRobinTopology);
    EitherAssert.assertThat(roundRobinResult).isLeft();
    assertThat(roundRobinResult.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("persisted zone-aware partition distribution config");

    // when / then: config absent
    final var noConfigResult =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of(ZONE_B_0))
            .operations(noConfigTopology);
    EitherAssert.assertThat(noConfigResult).isLeft();
    assertThat(noConfigResult.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("persisted zone-aware partition distribution config");
  }

  @Test
  void shouldRejectEmptyBrokers() {
    // given
    final var currentTopology = buildTopology(SINGLE_ZONE_CONFIG, SINGLE_ZONE_MEMBERS);

    // when
    final var result =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of()).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("at least one broker");
  }

  @Test
  void shouldRejectBrokersNotInTargetZone() {
    // given: brokers supplied belong to zone-a, not the failing-back zone-b
    final var currentTopology = buildTopology(SINGLE_ZONE_CONFIG, SINGLE_ZONE_MEMBERS);

    // when
    final var result =
        new FailbackRequestTransformer(ZONE_B, 1, 500, Set.of(ZONE_A_1))
            .operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("must belong to zone");
  }

  @Test
  void shouldRejectBrokerCountBelowNumberOfReplicas() {
    // given: only 1 broker supplied but 2 replicas requested
    final var currentTopology = buildTopology(SINGLE_ZONE_CONFIG, SINGLE_ZONE_MEMBERS);

    // when
    final var result =
        new FailbackRequestTransformer(ZONE_B, 2, 500, Set.of(ZONE_B_0))
            .operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("less than the requested number");
  }
}
