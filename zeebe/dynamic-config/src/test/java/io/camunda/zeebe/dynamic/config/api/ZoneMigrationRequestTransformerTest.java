/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.ZoneLayout;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class ZoneMigrationRequestTransformerTest {

  private static final String TENANT_A = "tenant-a";

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldMigrateSingleRegionCluster() {
    // given
    final var zones = SINGLE_REGION;
    final var initialTopology = unzonedTopology(3, 3, 3);
    final var configUpdatedTopology = setZoneAwareConfig(initialTopology, zones);

    // when
    final var result =
        plannedOperations(new ZoneMigrationRequestTransformer(ZONE_A), configUpdatedTopology);
    assertThat(result).isRight();
    assertThat(result.get())
        // check is unordered because member ordering between runs is not stable
        .containsExactly(
            new MemberJoinOperation(ZONE_A_0),
            new MemberJoinOperation(ZONE_A_1),
            new MemberJoinOperation(ZONE_A_2),
            new PartitionJoinOperation(ZONE_A_0, 1, 3, true),
            new PartitionPromoteOperation(ZONE_A_0, 1),
            new PartitionJoinOperation(ZONE_A_1, 1, 2, true),
            new PartitionPromoteOperation(ZONE_A_1, 1),
            new PartitionJoinOperation(ZONE_A_2, 1, 1, true),
            new PartitionPromoteOperation(ZONE_A_2, 1),
            new PartitionDemoteOperation(BARE_0, 1),
            new PartitionLeaveOperation(BARE_0, 1, 1),
            new PartitionDemoteOperation(BARE_1, 1),
            new PartitionLeaveOperation(BARE_1, 1, 1),
            new PartitionDemoteOperation(BARE_2, 1),
            new PartitionLeaveOperation(BARE_2, 1, 1),
            new PartitionJoinOperation(ZONE_A_0, 2, 1, true),
            new PartitionPromoteOperation(ZONE_A_0, 2),
            new PartitionJoinOperation(ZONE_A_1, 2, 3, true),
            new PartitionPromoteOperation(ZONE_A_1, 2),
            new PartitionJoinOperation(ZONE_A_2, 2, 2, true),
            new PartitionPromoteOperation(ZONE_A_2, 2),
            new PartitionDemoteOperation(BARE_0, 2),
            new PartitionLeaveOperation(BARE_0, 2, 1),
            new PartitionDemoteOperation(BARE_1, 2),
            new PartitionLeaveOperation(BARE_1, 2, 1),
            new PartitionDemoteOperation(BARE_2, 2),
            new PartitionLeaveOperation(BARE_2, 2, 1),
            new PartitionJoinOperation(ZONE_A_0, 3, 2, true),
            new PartitionPromoteOperation(ZONE_A_0, 3),
            new PartitionJoinOperation(ZONE_A_1, 3, 1, true),
            new PartitionPromoteOperation(ZONE_A_1, 3),
            new PartitionJoinOperation(ZONE_A_2, 3, 3, true),
            new PartitionPromoteOperation(ZONE_A_2, 3),
            new PartitionDemoteOperation(BARE_0, 3),
            new PartitionLeaveOperation(BARE_0, 3, 1),
            new PartitionDemoteOperation(BARE_1, 3),
            new PartitionLeaveOperation(BARE_1, 3, 1),
            new PartitionDemoteOperation(BARE_2, 3),
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
    final var result = plannedOperations(new ZoneMigrationRequestTransformer(ZONE_B), oldTopology);

    // then
    assertThat(result).isRight();
    final var operations = result.get();

    assertThat(operations)
        // check is unordered because member ordering between runs is not stable
        .containsExactly(
            new MemberJoinOperation(ZONE_B_0),
            new MemberJoinOperation(ZONE_B_1),
            new PartitionJoinOperation(ZONE_B_0, 1, 3, true),
            new PartitionPromoteOperation(ZONE_B_0, 1),
            new PartitionJoinOperation(ZONE_B_1, 1, 1, true),
            new PartitionPromoteOperation(ZONE_B_1, 1),
            new PartitionDemoteOperation(BARE_1, 1),
            new PartitionLeaveOperation(BARE_1, 1, 1),
            new PartitionDemoteOperation(BARE_3, 1),
            new PartitionLeaveOperation(BARE_3, 1, 1),
            new PartitionJoinOperation(ZONE_B_0, 2, 4, true),
            new PartitionPromoteOperation(ZONE_B_0, 2),
            new PartitionJoinOperation(ZONE_B_1, 2, 2, true),
            new PartitionPromoteOperation(ZONE_B_1, 2),
            new PartitionDemoteOperation(BARE_1, 2),
            new PartitionLeaveOperation(BARE_1, 2, 1),
            new PartitionDemoteOperation(BARE_3, 2),
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
    final var result = plannedOperations(new ZoneMigrationRequestTransformer(ZONE_A), alreadyZoned);

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
    final var result = plannedOperations(new ZoneMigrationRequestTransformer(ZONE_A), oldTopology);

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
    final var result = plannedOperations(new ZoneMigrationRequestTransformer(ZONE_A), oldTopology);

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
        plannedOperations(new ZoneMigrationRequestTransformer(ZONE_B), afterSecondaryMigration);

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
    final var result = plannedOperations(new ZoneMigrationRequestTransformer(ZONE_A), topology);

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
    final var result =
        plannedOperations(new ZoneMigrationRequestTransformer("unknown-zone"), topology);

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
    final var result =
        plannedOperations(new ZoneMigrationRequestTransformer(zoneName), oldTopology);
    assertThat(result).isRight();
    return TestTopologyChangeSimulator.apply(oldTopology, result.get());
  }

  /**
   * The given topology with its single partition group mirrored under a second physical tenant, so
   * every broker holds partitions of both tenants. Enough to tell a plan that saw every tenant's
   * partition group from one that only ever saw the default group.
   */
  private CurrentClusterConfiguration twoTenantCluster(final ClusterConfiguration topology) {
    final var single = CurrentClusterConfiguration.fromLegacy(topology);
    return new CurrentClusterConfiguration(
        single.version(),
        single.globalConfiguration(),
        Map.of(
            CurrentClusterConfiguration.DEFAULT_GROUP,
            single.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
            TENANT_A,
            single.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)),
        single.phasedChangeState());
  }

  private ClusterConfiguration setZoneAwareConfig(
      final ClusterConfiguration topology, final List<ZoneSpec> zones) {
    final var result =
        plannedOperations(
            new UpdatePartitionDistributionTransformer(new ZoneAwareConfig(zones)), topology);
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

  @Nested
  class Phases {

    /**
     * A stage replaces every broker of one zone, so leaving a tenant out does not merely deny it
     * the new brokers: its partitions stay on the member ids the stage removes. Both tenants
     * therefore have to move off both replaced brokers.
     */
    @Test
    void shouldMoveEveryTenantsPartitionsOffTheStagesBrokers() {
      // given — two tenants on a four-broker cluster staged for a dual-region migration, where
      // brokers 1 and 3 are the ones zone-b replaces
      final var configuration =
          twoTenantCluster(setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION));

      // when
      final var phases = new ZoneMigrationRequestTransformer(ZONE_B).phases(configuration);

      // then
      assertThat(phases).isRight();
      final var groupOperations = partitionPhase(phases.get()).groupOperations();
      assertThat(groupOperations)
          .describedAs("every tenant's partitions are replanned, not only the default tenant's")
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A);
      assertThat(groupOperations)
          .allSatisfy(
              (groupId, operations) -> {
                assertThat(operations)
                    .describedAs("tenant '%s' leaves both brokers zone-b replaces", groupId)
                    .filteredOn(PartitionLeaveOperation.class::isInstance)
                    .extracting(operation -> ((PartitionLeaveOperation) operation).memberId())
                    .contains(BARE_1, BARE_3)
                    .doesNotContain(BARE_0, BARE_2);
                assertThat(operations)
                    .describedAs("tenant '%s' places partitions on zone-b's new brokers", groupId)
                    .filteredOn(PartitionJoinOperation.class::isInstance)
                    .extracting(operation -> ((PartitionJoinOperation) operation).memberId())
                    .containsOnly(ZONE_B_0, ZONE_B_1);
              });
    }

    /**
     * The zoned ids a stage introduces are global state, so a plan that covers every tenant must
     * still join and leave each broker exactly once, around the partition work rather than
     * interleaved with it — a partition can only move onto a broker that has joined, and a broker
     * can only leave once it holds nothing.
     */
    @Test
    void shouldJoinAndLeaveEachBrokerOnceAroundThePartitionWork() {
      // given
      final var configuration =
          twoTenantCluster(setZoneAwareConfig(unzonedTopology(4, 2, 4), DUAL_REGION));

      // when
      final var phases = new ZoneMigrationRequestTransformer(ZONE_B).phases(configuration);

      // then
      assertThat(phases).isRight();
      assertThat(phases.get()).hasSize(3);
      assertThat(((GlobalPhase) phases.get().getFirst()).operations())
          .containsExactly(new MemberJoinOperation(ZONE_B_0), new MemberJoinOperation(ZONE_B_1));
      assertThat(((GlobalPhase) phases.get().getLast()).operations())
          .containsExactly(new MemberLeaveOperation(BARE_1), new MemberLeaveOperation(BARE_3));
    }

    @Test
    void shouldRejectAnInvalidRequestBeforePlanningAnyTenant() {
      // given — no zone-aware config persisted, so no stage can be derived
      final var configuration = twoTenantCluster(unzonedTopology(4, 2, 4));

      // when
      final var phases = new ZoneMigrationRequestTransformer(ZONE_B).phases(configuration);

      // then
      assertThat(phases)
          .isLeft()
          .left()
          .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
          .satisfies(
              e ->
                  assertThat(e)
                      .hasMessageContaining(
                          "Zone migration requires a persisted zone-aware partition distribution config, but was not set."));
    }

    private PartitionGroupPhase partitionPhase(final List<Phase> phases) {
      return phases.stream()
          .filter(PartitionGroupPhase.class::isInstance)
          .map(PartitionGroupPhase.class::cast)
          .findFirst()
          .orElseThrow(
              () -> new AssertionError("expected the plan to contain partition work: " + phases));
    }
  }
}
