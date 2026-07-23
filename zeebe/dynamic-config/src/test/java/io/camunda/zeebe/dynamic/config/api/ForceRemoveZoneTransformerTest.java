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
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ForceRemoveZoneTransformerTest {

  private static final DynamicPartitionConfig PARTITION_CONFIG = DynamicPartitionConfig.init();

  // 2 partitions, 2 zones: zone-a (1 replica, priority 1000), zone-b (1 replica, priority 500)
  // Members: zone-a_0, zone-b_0 — RF = 2
  private static final ZoneAwareConfig DUAL_ZONE_CONFIG =
      new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 500)));

  private static final Set<MemberId> DUAL_ZONE_MEMBERS = Set.of(ZONE_A_0, ZONE_B_0);

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
  void shouldForceRemoveZoneBrokersAndDropZoneFromConfig() {
    // given: dual-zone cluster, zone-b fails over
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);
    final var expectedConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));

    // when
    final var result = new ForceRemoveZoneTransformer(ZONE_B).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new PartitionForceReconfigureOperation(ZONE_A_0, 1, Set.of(ZONE_A_0)),
            new PartitionForceReconfigureOperation(ZONE_A_0, 2, Set.of(ZONE_A_0)),
            new MemberRemoveOperation(ZONE_A_0, ZONE_B_0),
            new UpdatePartitionDistributorConfigOperation(ZONE_A_0, expectedConfig));
  }

  @Test
  void shouldReturnForced() {
    assertThat(new ForceRemoveZoneTransformer(ZONE_B).isForced()).isTrue();
  }

  @Test
  void shouldRejectWhenNoZoneAwareConfigIsPersisted() {
    // given: a plain round-robin cluster
    final var plainMembers = Set.of(MemberId.from("0"), MemberId.from("1"));
    final var distribution =
        new RoundRobinPartitionDistributor().distributePartitions(plainMembers, PARTITION_IDS, 2);
    final var roundRobinTopology =
        ConfigurationUtil.getClusterConfigFrom(distribution, PARTITION_CONFIG, "c")
            .setPartitionDistributorConfig(new RoundRobinConfig());

    // when
    final var result = new ForceRemoveZoneTransformer(ZONE_A).operations(roundRobinTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("persisted zone-aware partition distribution config");
  }

  @Test
  void shouldRejectUnknownZone() {
    // given
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);

    // when
    final var result = new ForceRemoveZoneTransformer(ZONE_C).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("unknown zone");
  }

  @Test
  void shouldRejectFailoverThatWouldLeaveNoBrokers() {
    // given: config lists a surviving zone-a, but all live members are in zone-b
    final var singleZoneConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 1, 500)));
    final var ghostZoneConfig =
        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 1, 500), new ZoneSpec(ZONE_A, 1, 1000)));
    final var currentTopology =
        buildTopology(singleZoneConfig, Set.of(ZONE_B_0))
            .setPartitionDistributorConfig(ghostZoneConfig);

    // when
    final var result = new ForceRemoveZoneTransformer(ZONE_B).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("no brokers");
  }

  @Test
  void shouldRejectFailoverOfLastRemainingZone() {
    // given: a single-zone cluster
    final var singleZoneConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));
    final var currentTopology = buildTopology(singleZoneConfig, Set.of(ZONE_A_0));

    // when
    final var result = new ForceRemoveZoneTransformer(ZONE_A).operations(currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("last remaining zone");
  }
}
