/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.TENANT_A;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.partitionGroupPhase;
import static io.camunda.zeebe.dynamic.config.util.PhysicalTenantFixtures.withMirroredTenant;
import static io.camunda.zeebe.dynamic.config.util.ZoneFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.ZoneAwarePartitionDistributor;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class RemoveZoneTransformerTest {

  private static final DynamicPartitionConfig PARTITION_CONFIG = DynamicPartitionConfig.init();

  // 2 partitions, 2 zones: zone-a (1 replica, priority 1000), zone-b (1 replica, priority 500)
  // Members: zone-a_0, zone-b_0 — RF = 2
  private static final ZoneAwareConfig DUAL_ZONE_CONFIG =
      new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 500)));

  private static final Set<MemberId> DUAL_ZONE_MEMBERS = Set.of(ZONE_A_0, ZONE_B_0);

  private static final List<PartitionId> PARTITION_IDS =
      IntStream.rangeClosed(1, 2)
          .mapToObj(i -> new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, i))
          .toList();

  private static CurrentClusterConfiguration buildTopology(
      final ZoneAwareConfig config, final Set<MemberId> members) {
    final var distribution =
        new ZoneAwarePartitionDistributor(config.zones())
            .distributePartitions(members, PARTITION_IDS, config.replicationFactor());
    final var topology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            members,
            distribution,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_CONFIG),
            "c");
    return topology.updateGlobalConfiguration(
        globalConfiguration -> globalConfiguration.setPartitionDistributorConfig(config));
  }

  @Test
  void shouldForceRemoveZoneBrokersAndDropZoneFromConfig() {
    // given: dual-zone cluster, zone-b fails over
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);
    final var expectedConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));

    // when -- forced = true
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_B, true), currentTopology);

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
  void shouldRemoveZoneBrokersAndDropZoneFromConfig() {
    // given: dual-zone cluster, zone-b fails over
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);
    final var expectedConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));

    // when -- forced = false
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_B, false), currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .containsExactly(
            new UpdatePartitionDistributorConfigOperation(ZONE_A_0, expectedConfig),
            new PartitionDemoteOperation(ZONE_B_0, 1),
            new PartitionLeaveOperation(ZONE_B_0, 1, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 1, 1),
            new PartitionDemoteOperation(ZONE_B_0, 2),
            new PartitionLeaveOperation(ZONE_B_0, 2, 1),
            new PartitionReconfigurePriorityOperation(ZONE_A_0, 2, 1),
            new MemberLeaveOperation(ZONE_B_0));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRespectSkipScalingOperations(final boolean skipScalingOperations) {
    // given
    final var currentTopology =
        buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS)
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration.setPartitionDistributorConfig(
                        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)))));

    // when
    final var result =
        plannedOperations(
            new ScaleRequestTransformer(Set.of(ZONE_A_0), Optional.of(1), skipScalingOperations),
            currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
    if (skipScalingOperations) {
      assertThat(result.get())
          .noneMatch(
              operation ->
                  operation instanceof PreScalingOperation
                      || operation instanceof PostScalingOperation);
    } else {
      assertThat(result.get())
          .contains(
              new PreScalingOperation(ZONE_A_0, Set.of(ZONE_A_0)),
              new PostScalingOperation(ZONE_A_0, Set.of(ZONE_A_0)));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldReportWhetherRemovalIsForced(final boolean force) {
    assertThat(new RemoveZoneTransformer(ZONE_B, force).isForced()).isEqualTo(force);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRejectWhenNoZoneAwareConfigIsPersisted(final boolean force) {
    // given: a plain round-robin cluster
    final var plainMembers = Set.of(MemberId.from("0"), MemberId.from("1"));
    final var distribution =
        new RoundRobinPartitionDistributor().distributePartitions(plainMembers, PARTITION_IDS, 2);
    final var roundRobinTopology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
                plainMembers,
                distribution,
                Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, PARTITION_CONFIG),
                "c")
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration.setPartitionDistributorConfig(new RoundRobinConfig()));

    // when
    final var result =
        plannedOperations(new RemoveZoneTransformer(ZONE_A, force), roundRobinTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("persisted zone-aware partition distribution config");
  }

  @Test
  void shouldGracefullyRemoveZoneContainingTheElectedCoordinator() {
    // given: zone-a holds the elected coordinator (lowest member id, ZONE_A_0)
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);

    // when
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_A, false), currentTopology);

    // then: scaling callbacks are skipped because the removed zone releases its leases as it shuts
    // down
    EitherAssert.assertThat(result).isRight();
    assertThat(result.get())
        .contains(new MemberLeaveOperation(ZONE_A_0))
        .noneMatch(
            operation ->
                operation instanceof PreScalingOperation
                    || operation instanceof PostScalingOperation);
  }

  @Test
  void shouldAllowForcedRemovalOfTheZoneContainingTheElectedCoordinator() {
    // given: zone-a holds the elected coordinator (lowest member id, ZONE_A_0)
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);

    // when
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_A, true), currentTopology);

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRejectUnknownZone(final boolean force) {
    // given
    final var currentTopology = buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS);

    // when
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_C, force), currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("unknown zone");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRejectFailoverThatWouldLeaveNoBrokers(final boolean force) {
    // given: config lists a surviving zone-a, but all live members are in zone-b
    final var singleZoneConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 1, 500)));
    final var ghostZoneConfig =
        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_B, 1, 500), new ZoneSpec(ZONE_A, 1, 1000)));
    final var currentTopology =
        buildTopology(singleZoneConfig, Set.of(ZONE_B_0))
            .updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration.setPartitionDistributorConfig(ghostZoneConfig));

    // when
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_B, force), currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("no brokers");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRejectFailoverOfLastRemainingZone(final boolean force) {
    // given: a single-zone cluster
    final var singleZoneConfig = new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));
    final var currentTopology = buildTopology(singleZoneConfig, Set.of(ZONE_A_0));

    // when
    final var result = plannedOperations(new RemoveZoneTransformer(ZONE_A, force), currentTopology);

    // then
    EitherAssert.assertThat(result).isLeft();
    assertThat(result.getLeft())
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .hasMessageContaining("last remaining zone");
  }

  @Nested
  class Phases {

    private static final ZoneAwareConfig SURVIVING_CONFIG =
        new ZoneAwareConfig(List.of(new ZoneSpec(ZONE_A, 1, 1000)));

    /**
     * A zone fails over for the whole cluster, so every tenant's partitions have to be handed to
     * the brokers outside it. A tenant left out keeps replicas in the failed zone — and the member
     * removal that follows refuses the whole failover over it.
     */
    @Test
    void shouldEvictTheFailedZoneFromEveryPhysicalTenant() {
      // given
      final var configuration =
          withMirroredTenant(buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS));

      // when — zone-b fails over
      final var phases = new RemoveZoneTransformer(ZONE_B, true).phases(configuration);

      // then
      EitherAssert.assertThat(phases).isRight();
      assertThat(partitionGroupPhase(phases.get()).groupOperations())
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)
          .allSatisfy(
              (physicalTenantId, operations) ->
                  assertThat(operations)
                      .describedAs("partitions of physical tenant '%s'", physicalTenantId)
                      .containsExactly(
                          new PartitionForceReconfigureOperation(ZONE_A_0, 1, Set.of(ZONE_A_0)),
                          new PartitionForceReconfigureOperation(ZONE_A_0, 2, Set.of(ZONE_A_0))));
    }

    /**
     * The shrunk layout is persisted in the same phase that removes the zone's brokers, so the
     * cluster never describes a zone layout its members do not match.
     */
    @Test
    void shouldGracefullyRemoveReplicasFromEveryPhysicalTenant() {
      // given
      final var configuration =
          withMirroredTenant(buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS));

      // when
      final var phases = new RemoveZoneTransformer(ZONE_B, false).phases(configuration);

      // then — persist the shrunk layout, remove every tenant's replicas, then remove the broker
      EitherAssert.assertThat(phases).isRight();
      assertThat(phases.get()).hasSize(3);
      assertThat(((GlobalPhase) phases.get().getFirst()).operations())
          .containsExactly(
              new UpdatePartitionDistributorConfigOperation(ZONE_A_0, SURVIVING_CONFIG));
      assertThat(phases.get().get(1)).isInstanceOf(PartitionGroupPhase.class);

      assertThat(partitionGroupPhase(phases.get()).groupOperations())
          .containsOnlyKeys(CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_A)
          .allSatisfy(
              (physicalTenantId, operations) ->
                  assertThat(operations)
                      .describedAs("partitions of physical tenant '%s'", physicalTenantId)
                      .filteredOn(PartitionLeaveOperation.class::isInstance)
                      .extracting(operation -> ((PartitionLeaveOperation) operation).memberId())
                      .contains(ZONE_B_0));

      assertThat(((GlobalPhase) phases.get().getLast()).operations())
          .contains(new MemberLeaveOperation(ZONE_B_0));
    }

    @Test
    void shouldDropTheZoneFromTheLayoutAsTheBrokersAreRemoved() {
      // given
      final var configuration =
          withMirroredTenant(buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS));

      // when
      final var phases = new RemoveZoneTransformer(ZONE_B, true).phases(configuration);

      // then
      EitherAssert.assertThat(phases).isRight();
      assertThat(phases.get()).hasSize(2);
      assertThat(((GlobalPhase) phases.get().getLast()).operations())
          .containsExactly(
              new MemberRemoveOperation(ZONE_A_0, ZONE_B_0),
              new UpdatePartitionDistributorConfigOperation(ZONE_A_0, SURVIVING_CONFIG));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectAnInvalidRequestBeforePlanningAnyTenant(final boolean force) {
      // given
      final var configuration =
          withMirroredTenant(buildTopology(DUAL_ZONE_CONFIG, DUAL_ZONE_MEMBERS));

      // when
      final var phases = new RemoveZoneTransformer(ZONE_C, force).phases(configuration);

      // then
      EitherAssert.assertThat(phases)
          .isLeft()
          .left()
          .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
          .satisfies(error -> assertThat(error).hasMessageContaining("unknown zone"));
    }
  }
}
