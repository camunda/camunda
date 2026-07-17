/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.dynamic.config.transformer;

import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.ZoneLayout;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ZoneMigrationRequestTransformerTest {

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldMigrateSingleRegionCluster() {
    // given
    final var zones = SINGLE_REGION;
    final var initialTopology = unzonedTopology(3, 3, 3);
    final var configUpdatedTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var result =
        new ZoneMigrationRequestTransformer(ZONE_A).operations(configUpdatedTopology);
    assertThat(result).isRight();
    assertThat(result.get())
        // check is unordered because member ordering between runs is not stable
        .containsExactly(
            new MemberJoinOperation(ZONE_A_0),
            new MemberJoinOperation(ZONE_A_1),
            new MemberJoinOperation(ZONE_A_2),
            new PartitionJoinOperation(ZONE_A_0, 1, 3),
            new PartitionJoinOperation(ZONE_A_1, 1, 2),
            new PartitionJoinOperation(ZONE_A_2, 1, 1),
            new PartitionLeaveOperation(BARE_0, 1, 1),
            new PartitionLeaveOperation(BARE_1, 1, 1),
            new PartitionLeaveOperation(BARE_2, 1, 1),
            new PartitionJoinOperation(ZONE_A_0, 2, 1),
            new PartitionJoinOperation(ZONE_A_1, 2, 3),
            new PartitionJoinOperation(ZONE_A_2, 2, 2),
            new PartitionLeaveOperation(BARE_0, 2, 1),
            new PartitionLeaveOperation(BARE_1, 2, 1),
            new PartitionLeaveOperation(BARE_2, 2, 1),
            new PartitionJoinOperation(ZONE_A_0, 3, 2),
            new PartitionJoinOperation(ZONE_A_1, 3, 1),
            new PartitionJoinOperation(ZONE_A_2, 3, 3),
            new PartitionLeaveOperation(BARE_0, 3, 1),
            new PartitionLeaveOperation(BARE_1, 3, 1),
            new PartitionLeaveOperation(BARE_2, 3, 1),
            new MemberLeaveOperation(BARE_0),
            new MemberLeaveOperation(BARE_1),
            new MemberLeaveOperation(BARE_2));
    final var newTopology = TestTopologyChangeSimulator.apply(configUpdatedTopology, result.get());

    // then
    assertThat(newTopology.isFullyZoneAware()).isTrue();
    assertThat(newTopology.partitionDistributorConfig()).hasValue(new ZoneAwareConfig(zones));
    assertThat(newTopology.members().keySet())
        .containsExactlyInAnyOrder(ZONE_A_0, ZONE_A_1, ZONE_A_2);
    assertSamePartitionDistribution(
        initialTopology, newTopology, nodeMapping(IntStream.range(0, 3), ZONE_A));
  }

  @Test
  void shouldMigrateOnlySelectedZoneUsingScaleRequestTransformer() {
    // given
    final var oldTopology = setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_B).operations(oldTopology);

    // then
    assertThat(result).isRight();
    final var operations = result.get();

    assertThat(operations)
        // check is unordered because member ordering between runs is not stable
        .containsExactly(
            new MemberJoinOperation(ZONE_B_0),
            new MemberJoinOperation(ZONE_B_1),
            new PartitionJoinOperation(ZONE_B_0, 1, 3),
            new PartitionJoinOperation(ZONE_B_1, 1, 1),
            new PartitionLeaveOperation(BARE_1, 1, 1),
            new PartitionLeaveOperation(BARE_3, 1, 1),
            new PartitionJoinOperation(ZONE_B_0, 2, 4),
            new PartitionJoinOperation(ZONE_B_1, 2, 2),
            new PartitionLeaveOperation(BARE_1, 2, 1),
            new PartitionLeaveOperation(BARE_3, 2, 1),
            new MemberLeaveOperation(BARE_1),
            new MemberLeaveOperation(BARE_3));
  }

  @Test
  void shouldMigrateDualRegionClusterInTwoSteps() {
    // given
    final var zones = DUAL_REGION;
    final var initialTopology = unzonedTopology(4, 2, 4);
    final var configUpdatedTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var afterSecondaryMigration = migrate(configUpdatedTopology, ZONE_B);
    final var finalTopology = migrate(afterSecondaryMigration, ZONE_A);

    // then
    assertThat(afterSecondaryMigration.isPartiallyZoneAware()).isTrue();
    assertThat(afterSecondaryMigration.partitionDistributorConfig())
        .hasValue(new ZoneAwareConfig(zones));
    assertThat(afterSecondaryMigration.members().keySet())
        .containsExactlyInAnyOrder(BARE_0, BARE_2, ZONE_B_0, ZONE_B_1);
    assertSamePartitionDistribution(
        initialTopology, afterSecondaryMigration, mixedDualRegionNodeMapping());

    assertThat(finalTopology.isFullyZoneAware()).isTrue();
    assertThat(finalTopology.partitionDistributorConfig()).hasValue(new ZoneAwareConfig(zones));
    assertThat(finalTopology.members().keySet())
        .containsExactlyInAnyOrder(ZONE_A_0, ZONE_A_1, ZONE_B_0, ZONE_B_1);
    assertSamePartitionDistribution(
        initialTopology, finalTopology, dualRegionNodeMapping(4, List.of(ZONE_A, ZONE_B)));
  }

  @Test
  void shouldPreserveDistributionWhenZoneNamesSortAgainstPhysicalLayout() {
    // given
    final var zones =
        List.of(new ZoneSpec("zzz-region", 1, 100), new ZoneSpec("aaa-region", 1, 100));
    final var initialTopology = unzonedTopology(6, 6, 2);
    final var oldTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var afterSecondaryMigration = migrate(oldTopology, "aaa-region");
    final var newTopology = migrate(afterSecondaryMigration, "zzz-region");

    // then
    assertThat(newTopology.isFullyZoneAware()).isTrue();
    final var expectedNodeMapping = dualRegionNodeMapping(6, List.of("zzz-region", "aaa-region"));
    assertSamePartitionDistribution(initialTopology, newTopology, expectedNodeMapping);
  }

  @Test
  void shouldRejectAlreadyZonedCluster() {
    // given
    final var zones = SINGLE_REGION;
    final var alreadyZoned = migrate(setZoneAwareConfig(unzonedTopology(3, 3, 3), zones), ZONE_A);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_A).operations(alreadyZoned);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration request targets zone 'zone-a' which has already been migrated"));
  }

  @Test
  void shouldRejectMissingPersistedZoneAwareConfig() {
    // given
    final var oldTopology = unzonedTopology(3, 3, 3);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_A).operations(oldTopology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration requires a persisted zone-aware partition distribution config, but was not set. Update the partition distribution before migrating brokers."));
  }

  @Test
  void shouldRejectInvalidPartitionDistribution() {
    // given
    final var oldTopology =
        unzonedTopology(3, 3, 3).setPartitionDistributorConfig(new RoundRobinConfig());

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_A).operations(oldTopology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration requires a persisted zone-aware partition distribution config, but was RoundRobinConfig. Update the partition distribution before migrating brokers."));
  }

  @Test
  void shouldRejectTargetingAnAlreadyMigratedZone() {
    // given
    final var afterSecondaryMigration =
        migrate(setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION), ZONE_B);

    // when
    final var result =
        new ZoneMigrationRequestTransformer(ZONE_B).operations(afterSecondaryMigration);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration request targets zone 'zone-b' which has already been migrated."));
  }

  @Test
  void shouldRejectMigratingPrimaryZoneBeforeSecondaryZone() {
    // given
    final var topology = setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_A).operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration must proceed from the highest remaining zone index to the lowest.")
                    .hasMessageContaining("Expected next zoneIndex 1 but got 0"));
  }

  @Test
  void shouldRejectUnknownZone() {
    // given
    final var topology = setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION);

    // when
    final var result = new ZoneMigrationRequestTransformer("unknown-zone").operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Zone migration request targets unknown zone 'unknown-zone'"));
  }

  private ClusterConfiguration migrate(
      final ClusterConfiguration oldTopology, final String zoneName) {
    final var result = new ZoneMigrationRequestTransformer(zoneName).operations(oldTopology);
    assertThat(result).isRight();
    return TestTopologyChangeSimulator.apply(oldTopology, result.get());
  }

  private ClusterConfiguration setZoneAwareConfig(
      final ClusterConfiguration topology, final List<ZoneSpec> zones) {
    final var result =
        new UpdatePartitionDistributionTransformer(new ZoneAwareConfig(zones)).operations(topology);
    assertThat(result).isRight();
    return TestTopologyChangeSimulator.apply(topology, result.get());
  }

  private void assertSamePartitionDistribution(
      final ClusterConfiguration oldTopology,
      final ClusterConfiguration newTopology,
      final Map<MemberId, MemberId> nodeMapping) {
    final Map<Integer, Set<MemberId>> expected =
        partitionToMembers(oldTopology).entrySet().stream()
            // remap bare ids to zoned ids to compare
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().map(nodeMapping::get).collect(Collectors.toSet())));
    assertThat(partitionToMembers(newTopology))
        .describedAs("partition distribution must be preserved (each partition keeps its slots)")
        .isEqualTo(expected);
  }

  private int indexOf(
      final List<ClusterConfigurationChangeOperation> operations,
      final ClusterConfigurationChangeOperation expectedOperation) {
    final var index = operations.indexOf(expectedOperation);
    assertThat(index)
        .describedAs("expected operation %s to be part of the migration plan", expectedOperation)
        .isNotNegative();
    return index;
  }

  private Map<Integer, Set<MemberId>> partitionToMembers(final ClusterConfiguration topology) {
    return ConfigurationUtil.getPartitionDistributionFrom(topology, "temp").stream()
        .collect(Collectors.toMap(p -> p.id().number(), p -> Set.copyOf(p.members())));
  }

  private ClusterConfiguration unzonedTopology(
      final int clusterSize, final int partitionCount, final int replicationFactor) {
    final Set<MemberId> members =
        IntStream.range(0, clusterSize).mapToObj(MemberId::from).collect(Collectors.toSet());
    final Set<PartitionMetadata> distribution =
        new RoundRobinConfig()
            .toDistributor()
            .distributePartitions(members, sortedPartitionIds(partitionCount), replicationFactor);
    var topology = ConfigurationUtil.getClusterConfigFrom(distribution, partitionConfig, "cid");
    for (final MemberId member : members) {
      if (!topology.hasMember(member)) {
        topology = topology.addMember(member, MemberState.initializeAsActive(Map.of()));
      }
    }
    return topology;
  }

  private List<PartitionId> sortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId("temp", id))
        .collect(Collectors.toList());
  }

  private Map<MemberId, MemberId> nodeMapping(final IntStream sourceIndices, final String zone) {
    return sourceIndices
        .boxed()
        .collect(Collectors.toMap(MemberId::from, i -> MemberId.from(zone, i)));
  }

  private Map<MemberId, MemberId> mixedDualRegionNodeMapping() {
    final var nodeMapping = new HashMap<MemberId, MemberId>();
    nodeMapping.put(BARE_0, BARE_0);
    nodeMapping.put(BARE_1, ZONE_B_0);
    nodeMapping.put(BARE_2, BARE_2);
    nodeMapping.put(BARE_3, ZONE_B_1);
    return nodeMapping;
  }

  private Map<MemberId, MemberId> dualRegionNodeMapping(
      final int clusterSize, final List<String> zones) {
    final var nodeMapping = new HashMap<MemberId, MemberId>();
    for (int i = 0; i < clusterSize; i++) {
      final var zoneIdx = ZoneLayout.zoneRankForBareNodeIdx(i, zones.size());
      nodeMapping.put(
          MemberId.from(i),
          MemberId.from(zones.get(zoneIdx), ZoneLayout.localNodeIdxForBareNodeIdx(i, 2)));
    }
    return nodeMapping;
  }
}
