/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class ZoneMigrationRequestTransformerTest {

  private static final String ZONE_A = "us-east-1";
  private static final String ZONE_B = "us-west-1";
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldMigrateSingleRegionCluster() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 3, 100));
    final var initialTopology = unzonedTopology(3, 3, 3);
    final var configUpdatedTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var newTopology = migrate(configUpdatedTopology, 0);

    // then
    assertThat(newTopology.isFullyZoneAware()).isTrue();
    assertThat(newTopology.partitionDistributorConfig()).hasValue(new ZoneAwareConfig(zones));
    assertThat(newTopology.members().keySet())
        .containsExactlyInAnyOrder(
            MemberId.from(ZONE_A, 0), MemberId.from(ZONE_A, 1), MemberId.from(ZONE_A, 2));
    assertSamePartitionDistribution(
        initialTopology, newTopology, nodeMapping(IntStream.range(0, 3), ZONE_A));
  }

  @Test
  void shouldMigrateOnlySelectedZoneUsingScaleRequestTransformer() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var oldTopology = setZoneAwareConfig(unzonedTopology(4, 2, 4), zones);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_B).operations(oldTopology);

    // then
    assertThat(result).isRight();
    final var operations = result.get();

    final var secondaryLastLeaveIndex =
        indexOf(operations, new MemberLeaveOperation(MemberId.from(3)));
    assertThat(operations.subList(0, secondaryLastLeaveIndex + 1))
        .extracting(ClusterConfigurationChangeOperation::memberId)
        .contains(
            MemberId.from(ZONE_B, 0), MemberId.from(ZONE_B, 1), MemberId.from(1), MemberId.from(3))
        .doesNotContain(
            MemberId.from(ZONE_A, 0), MemberId.from(ZONE_A, 1), MemberId.from(0), MemberId.from(2));
    assertThat(operations)
        .filteredOn(MemberJoinOperation.class::isInstance)
        .extracting(ClusterConfigurationChangeOperation::memberId)
        .doesNotContain(MemberId.from(ZONE_A, 0), MemberId.from(ZONE_A, 1));
  }

  @Test
  void shouldMigrateDualRegionClusterInTwoSteps() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var initialTopology = unzonedTopology(4, 2, 4);
    final var configUpdatedTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var afterSecondaryMigration = migrate(configUpdatedTopology, 1);
    final var finalTopology = migrate(afterSecondaryMigration, 0);

    // then
    assertThat(afterSecondaryMigration.isPartiallyZoneAware()).isTrue();
    assertThat(afterSecondaryMigration.partitionDistributorConfig())
        .hasValue(new ZoneAwareConfig(zones));
    assertThat(afterSecondaryMigration.members().keySet())
        .containsExactlyInAnyOrder(
            MemberId.from(0), MemberId.from(2), MemberId.from(ZONE_B, 0), MemberId.from(ZONE_B, 1));
    assertSamePartitionDistribution(
        initialTopology, afterSecondaryMigration, mixedDualRegionNodeMapping());

    assertThat(finalTopology.isFullyZoneAware()).isTrue();
    assertThat(finalTopology.partitionDistributorConfig()).hasValue(new ZoneAwareConfig(zones));
    assertThat(finalTopology.members().keySet())
        .containsExactlyInAnyOrder(
            MemberId.from(ZONE_A, 0),
            MemberId.from(ZONE_A, 1),
            MemberId.from(ZONE_B, 0),
            MemberId.from(ZONE_B, 1));
    assertSamePartitionDistribution(
        initialTopology, finalTopology, dualRegionNodeMapping(4, ZONE_A, ZONE_B));
  }

  @Test
  void shouldPreserveDistributionWhenZoneNamesSortAgainstPhysicalLayout() {
    // given
    final var zones =
        List.of(new ZoneSpec("zzz-region", 1, 100), new ZoneSpec("aaa-region", 1, 100));
    final var initialTopology = unzonedTopology(6, 6, 2);
    final var oldTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var afterSecondaryMigration = migrate(oldTopology, 1);
    final var newTopology = migrate(afterSecondaryMigration, 0);

    // then
    assertThat(newTopology.isFullyZoneAware()).isTrue();
    final var expectedNodeMapping = dualRegionNodeMapping(6, "zzz-region", "aaa-region");
    assertSamePartitionDistribution(initialTopology, newTopology, expectedNodeMapping);
  }

  @Test
  void shouldRejectAlreadyZonedCluster() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 3, 100));
    final var alreadyZoned = migrate(setZoneAwareConfig(unzonedTopology(3, 3, 3), zones), 0);

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
                        "Zone migration is only supported while the cluster still contains bare brokers, but the current cluster is already fully zone-aware."));
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
                        "Zone migration requires a persisted zone-aware partition distribution config"));
  }

  @Test
  void shouldRejectTargetingAnAlreadyMigratedZone() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var afterSecondaryMigration =
        migrate(setZoneAwareConfig(unzonedTopology(4, 2, 4), zones), 1);

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
                        "Zone migration request targets zone 'us-west-1' which has already been migrated."));
  }

  @Test
  void shouldRejectMigratingPrimaryZoneBeforeSecondaryZone() {
    // given
    final var zones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var topology = setZoneAwareConfig(unzonedTopology(4, 2, 4), zones);

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
    final var zones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var topology = setZoneAwareConfig(unzonedTopology(4, 2, 4), zones);

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

  @Test
  void shouldRejectPersistedZoneOrderThatDoesNotMatchTheCurrentMixedTopology() {
    // given
    final var originalZones = List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var afterSecondaryMigration =
        migrate(setZoneAwareConfig(unzonedTopology(4, 2, 4), originalZones), 1);
    final var swappedZones = List.of(new ZoneSpec(ZONE_B, 2, 100), new ZoneSpec(ZONE_A, 2, 100));
    final var mismatchedConfigTopology =
        afterSecondaryMigration.setPartitionDistributorConfig(new ZoneAwareConfig(swappedZones));

    // when
    final var result =
        new ZoneMigrationRequestTransformer(ZONE_A).operations(mismatchedConfigTopology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining(
                        "Current topology is incompatible with the persisted zone migration plan: multiple members map to slot 0. Check the persisted zone order."));
  }

  @Test
  void shouldRejectWhenAZoneGetsFewerBrokerSlotsThanItsReplicas() {
    // given — 3 bare brokers, RF=3. Under the nodeIdx % zoneCount assignment the first zone owns
    // slots {0,2} (2 brokers) and the second owns slot {1} (1 broker). Declaring the second zone
    // with 2 replicas is therefore impossible to realise, even though the replica sum equals RF.
    final var zones = List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 2, 100));
    final var topology = setZoneAwareConfig(unzonedTopology(3, 3, 3), zones);

    // when
    final var result = new ZoneMigrationRequestTransformer(ZONE_B).operations(topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(
            e ->
                assertThat(e)
                    .hasMessageContaining("us-west-1")
                    .hasMessageContaining("2 replicas")
                    .hasMessageContaining("1 broker slot"));
  }

  private ClusterConfiguration migrate(
      final ClusterConfiguration oldTopology, final int zoneIndex) {
    final var zoneName =
        oldTopology
            .partitionDistributorConfig()
            .map(ZoneAwareConfig.class::cast)
            .orElseThrow()
            .zones()
            .get(zoneIndex)
            .name();
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
    nodeMapping.put(MemberId.from(0), MemberId.from(0));
    nodeMapping.put(MemberId.from(1), MemberId.from(ZONE_B, 0));
    nodeMapping.put(MemberId.from(2), MemberId.from(2));
    nodeMapping.put(MemberId.from(3), MemberId.from(ZONE_B, 1));
    return nodeMapping;
  }

  private Map<MemberId, MemberId> dualRegionNodeMapping(
      final int clusterSize, final String zoneA, final String zoneB) {
    final var nodeMapping = new HashMap<MemberId, MemberId>();
    int localA = 0;
    int localB = 0;
    for (int i = 0; i < clusterSize; i++) {
      if (i % 2 == 0) {
        nodeMapping.put(MemberId.from(i), MemberId.from(zoneA, localA++));
      } else {
        nodeMapping.put(MemberId.from(i), MemberId.from(zoneB, localB++));
      }
    }
    return nodeMapping;
  }
}
