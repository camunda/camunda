/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.dynamic.config.api.TestChangePlan.plannedOperations;
import static io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration.DEFAULT_GROUP;
import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ClusterScaleRequestTransformerTest {

  private static final String ZONE_A = "zoneA";
  private static final String ZONE_B = "zoneB";
  private static final int ZONE_B_BROKERS = 1;
  private static final int ZONE_B_REPLICAS = 1;
  private static final int ZONE_A_PRIORITY = 1000;
  private static final int ZONE_B_PRIORITY = 500;
  private static final String TENANT_B = "tenant-b";
  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");

  @Property(tries = 10)
  void shouldScaleBrokersWhenPartitionsUnchanged(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        2,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScaleBrokersWhenPartitionsUnchangedWhenZoned(
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 2, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 2, max = 100) final int newClusterSize) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        2,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
  }

  @Property(tries = 10)
  void shouldScalePartitionsWhenClusterSizeUnchanged(
      @ForAll @IntRange(min = 3, max = 100) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        clusterSize,
        Optional.empty(),
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScalePartitionsWhenClusterSizeUnchangedWhenZoned(
      @ForAll @IntRange(min = 3, max = 100) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        clusterSize,
        Optional.empty(),
        Optional.of(ZONE_A));
  }

  @Property(tries = 10)
  void shouldChangeReplicationFactorWhenClusterSizeAndPartitionsUnchanged(
      @ForAll @IntRange(min = 5, max = 10) final int clusterSize,
      @ForAll @IntRange(min = 1, max = 100) final int partitionCount,
      @ForAll @IntRange(min = 1, max = 5) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 5) final int newReplicationFactor) {
    shouldScaleBrokersAndPartitionsByCount(
        partitionCount,
        Optional.empty(),
        oldReplicationFactor,
        Optional.of(newReplicationFactor),
        clusterSize,
        Optional.empty(),
        // replication factor cannot be changed for a zoned cluster
        Optional.empty());
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitions(
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitionsWhenZoned(
      @ForAll @IntRange(min = 3, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 3, max = 100) final int newClusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        3,
        Optional.empty(),
        oldClusterSize,
        Optional.of(newClusterSize),
        Optional.of(ZONE_A));
  }

  @Property(tries = 10)
  void shouldScaleBrokersAndPartitionsAndChangeReplicationFactor(
      @ForAll @IntRange(min = 5, max = 100) final int oldClusterSize,
      @ForAll @IntRange(min = 5, max = 100) final int newClusterSize,
      @ForAll @IntRange(min = 1, max = 10) final int oldPartitionCount,
      @ForAll @IntRange(min = 10, max = 20) final int newPartitionCount,
      @ForAll @IntRange(min = 1, max = 5) final int oldReplicationFactor,
      @ForAll @IntRange(min = 1, max = 5) final int newReplicationFactor) {
    shouldScaleBrokersAndPartitionsByCount(
        oldPartitionCount,
        Optional.of(newPartitionCount),
        oldReplicationFactor,
        Optional.of(newReplicationFactor),
        oldClusterSize,
        Optional.of(newClusterSize),
        // replication factor cannot be changed for a zoned cluster
        Optional.empty());
  }

  void shouldScaleBrokersAndPartitionsByCount(
      final int oldPartitionCount,
      final Optional<Integer> newPartitionCount,
      final int replicationFactor,
      final Optional<Integer> newReplicationFactor,
      final int oldClusterSize,
      final Optional<Integer> newClusterSize,
      final Optional<String> zone) {
    // given
    final var effectiveConfig =
        zone.isPresent() ? zoneAwareConfig(replicationFactor) : new RoundRobinConfig();
    final var effectiveNewConfig =
        zone.isPresent()
            ? zoneAwareConfig(newReplicationFactor.orElse(replicationFactor))
            : new RoundRobinConfig();
    final var oldMembers =
        zone.isPresent()
            ? scaledMembers(oldClusterSize)
            : membersInZone(Optional.empty(), oldClusterSize);
    final var newMembers =
        zone.isPresent()
            ? scaledMembers(newClusterSize.orElse(oldClusterSize))
            : membersInZone(Optional.empty(), newClusterSize.orElse(oldClusterSize));

    final var expectedNewDistribution =
        effectiveNewConfig
            .toDistributor()
            .distributePartitions(
                newMembers,
                getSortedPartitionIds(newPartitionCount.orElse(oldPartitionCount)),
                zone.isPresent()
                    ? ((ZoneAwareConfig) effectiveNewConfig).replicationFactor()
                    : newReplicationFactor.orElse(replicationFactor));

    final var oldDistribution =
        effectiveConfig
            .toDistributor()
            .distributePartitions(
                oldMembers,
                getSortedPartitionIds(oldPartitionCount),
                // note that it's not really used for zone-aware
                zone.isPresent()
                    ? ((ZoneAwareConfig) effectiveConfig).replicationFactor()
                    : replicationFactor);
    CurrentClusterConfiguration oldClusterTopology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            oldMembers,
            oldDistribution,
            Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, partitionConfig),
            "clusterId");
    if (zone.isPresent()) {
      oldClusterTopology =
          oldClusterTopology.updateGlobalConfiguration(
              globalConfiguration ->
                  globalConfiguration.setPartitionDistributorConfig(effectiveConfig));
    }
    for (final MemberId member : oldMembers) {
      if (!oldClusterTopology.globalConfiguration().hasMember(member)) {
        oldClusterTopology =
            oldClusterTopology.updateGlobalConfiguration(
                globalConfiguration ->
                    globalConfiguration.addMember(member, BrokerState.initializeAsActive()));
      }
    }
    // when
    final var patchRequest =
        new ClusterScaleRequest(
            newClusterSize, newPartitionCount, newReplicationFactor, zone, false);

    applyRequestAndVerifyResultingTopology(
        newPartitionCount.orElse(oldPartitionCount),
        newMembers,
        patchRequest,
        oldClusterTopology,
        expectedNewDistribution,
        zone);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ClusterScaleRequest patchRequest,
      final CurrentClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution,
      final Optional<String> zone) {

    // when
    final var phasesResult =
        new ClusterScaleRequestTransformer(
                patchRequest.brokerCount(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor(),
                zone)
            .phases(oldClusterTopology);
    assertThat(phasesResult).isRight();
    final var phases = phasesResult.get();
    final var operations = TestChangePlan.flatten(phases);

    // apply phases to generate new topology
    final var newTopology = TestTopologyChangeSimulator.apply(oldClusterTopology, phases);

    // then
    final var newDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(
            newTopology, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    assertThat(newTopology.getMembers())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    assertThat(
            newTopology
                .partitionGroup(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
                .partitionCount())
        .isEqualTo(partitionCount);
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId(DEFAULT_GROUP, id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> membersInZone(final Optional<String> zone, final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(idx -> MemberId.from(zone.orElse(null), idx))
        .collect(Collectors.toSet());
  }

  @Test
  void shouldRejectZoneWithReplicationFactor() {
    // given
    final var topology = zoneAwareTopology(2, 2, 3);

    // when
    final var result =
        plannedOperations(
            new ClusterScaleRequestTransformer(
                Optional.of(3), Optional.empty(), Optional.of(3), Optional.of(ZONE_A)),
            topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  @Test
  void shouldRejectUnknownZone() {
    // given
    final var topology = zoneAwareTopology(2, 2, 3);

    // when
    final var result =
        plannedOperations(
            new ClusterScaleRequestTransformer(
                Optional.of(3), Optional.empty(), Optional.empty(), Optional.of("zoneX")),
            topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class)
        .satisfies(e -> Assertions.assertThat(e).hasMessageContaining("Unknown zone 'zoneX'"));
  }

  @Test
  void shouldRejectNonZoneAwareCluster() {
    // given
    final var members = membersInZone(Optional.of(ZONE_A), 3);
    final var distribution =
        new RoundRobinConfig()
            .toDistributor()
            .distributePartitions(members, getSortedPartitionIds(3), 1);
    final var topology =
        ConfigurationUtil.getCurrentClusterConfigurationFrom(
            members, distribution, Map.of(DEFAULT_GROUP, partitionConfig), "clusterId");

    // when
    final var result =
        plannedOperations(
            new ClusterScaleRequestTransformer(
                Optional.of(4), Optional.empty(), Optional.empty(), Optional.of(ZONE_A)),
            topology);

    // then
    assertThat(result)
        .isLeft()
        .left()
        .isInstanceOf(ClusterConfigurationRequestFailedException.InvalidRequest.class);
  }

  private CurrentClusterConfiguration zoneAwareTopology(
      final int zoneABrokers, final int zoneAReplicas, final int partitionCount) {
    final var config = zoneAwareConfig(zoneAReplicas);
    final var members = scaledMembers(zoneABrokers);
    final var distribution =
        config
            .toDistributor()
            .distributePartitions(
                members, getSortedPartitionIds(partitionCount), config.replicationFactor());
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
            members, distribution, Map.of(DEFAULT_GROUP, partitionConfig), "clusterId")
        .updateGlobalConfiguration(
            globalConfiguration -> globalConfiguration.setPartitionDistributorConfig(config));
  }

  private ZoneAwareConfig zoneAwareConfig(final int zoneAReplicas) {
    return new ZoneAwareConfig(
        List.of(
            new ZoneSpec(ZONE_A, zoneAReplicas, ZONE_A_PRIORITY),
            new ZoneSpec(ZONE_B, ZONE_B_REPLICAS, ZONE_B_PRIORITY)));
  }

  private Set<MemberId> scaledMembers(final int zoneABrokers) {
    final var members = new HashSet<>(membersInZone(Optional.of(ZONE_A), zoneABrokers));
    members.addAll(membersInZone(Optional.of(ZONE_B), ZONE_B_BROKERS));
    return members;
  }

  // -- physicalTenant scoping (phases()) --

  /**
   * Three brokers, replication factor 1, and a placement that already follows the cluster-wide
   * round robin over every tenant's partitions: the default tenant holds partitions 1 and 2 on id0
   * and id1, and tenant-b, whose partition sorts after both of them, holds its single partition on
   * id2. Scaling a tenant here therefore has nothing to relocate, which makes any relocation the
   * planner does emit meaningful, and leaves id2 as the broker a tenant-blind placement would
   * strand.
   */
  private CurrentClusterConfiguration twoTenantCluster() {
    final var defaultGroup =
        Map.of(
            id0, Map.of(1, PartitionState.active(1, partitionConfig)),
            id1, Map.of(2, PartitionState.active(1, partitionConfig)));
    final var tenantB = Map.of(id2, Map.of(1, PartitionState.active(1, partitionConfig)));
    final var brokers =
        Map.of(
            id0, new BrokerState(1, Instant.EPOCH, BrokerState.State.ACTIVE),
            id1, new BrokerState(1, Instant.EPOCH, BrokerState.State.ACTIVE),
            id2, new BrokerState(1, Instant.EPOCH, BrokerState.State.ACTIVE));
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        new GlobalConfiguration(
            1, Optional.empty(), brokers, Optional.empty(), Optional.empty(), Optional.empty()),
        Map.of(DEFAULT_GROUP, group(defaultGroup), TENANT_B, group(tenantB)),
        PhasedChangeState.empty());
  }

  private PartitionGroupConfiguration group(
      final Map<MemberId, Map<Integer, PartitionState>> partitionsByMember) {
    return new PartitionGroupConfiguration(
        1,
        0,
        partitionsByMember.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey, e -> BrokerPartitionState.initialize(e.getValue()))),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }

  private ClusterScaleRequestTransformer scaleTransformer(
      final Optional<Integer> newPartitionCount, final Optional<String> physicalTenantId) {
    return new ClusterScaleRequestTransformer(
        Optional.empty(), newPartitionCount, Optional.empty(), Optional.empty(), physicalTenantId);
  }

  private static PartitionGroupPhase singlePhase(final Either<Exception, List<Phase>> result) {
    assertThat(result).isRight();
    Assertions.assertThat(result.get()).hasSize(1);
    return (PartitionGroupPhase) result.get().get(0);
  }

  private static List<PartitionBootstrapOperation> bootstraps(
      final PartitionGroupPhase phase, final String groupId) {
    return phase.groupOperations().getOrDefault(groupId, List.of()).stream()
        .filter(PartitionBootstrapOperation.class::isInstance)
        .map(PartitionBootstrapOperation.class::cast)
        .toList();
  }

  private static List<PartitionJoinOperation> joins(
      final PartitionGroupPhase phase, final String groupId) {
    return phase.groupOperations().getOrDefault(groupId, List.of()).stream()
        .filter(PartitionJoinOperation.class::isInstance)
        .map(PartitionJoinOperation.class::cast)
        .toList();
  }

  @Test
  void shouldTargetOnlyTheRequestedPhysicalTenantsPartitionGroup() {
    // given — tenant-b currently has 1 partition; scale it to 2
    final var transformer = scaleTransformer(Optional.of(2), Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — only tenant-b gains a partition, and no other tenant is touched at all
    final var phase = singlePhase(result);
    Assertions.assertThat(phase.groupOperations()).containsOnlyKeys(TENANT_B);
    Assertions.assertThat(bootstraps(phase, TENANT_B))
        .singleElement()
        .satisfies(op -> Assertions.assertThat(op.partitionId()).isEqualTo(2));
  }

  @Test
  void shouldDistributePartitionsOfEveryPhysicalTenantTogether() {
    // given — id0 and id1 each hold a partition of the default tenant, id2 one of tenant-b
    final var transformer = scaleTransformer(Optional.of(2), Optional.of(TENANT_B));

    // when — tenant-b gains a second partition
    final var result = transformer.phases(twoTenantCluster());

    // then — the placement continues the round robin across all four partitions instead of
    // restarting it within tenant-b, so tenant-b's existing partition stays on id2 and only the new
    // one is placed. Distributing tenant-b's partitions on their own would have moved that one onto
    // id0 and put the new one on id1, leaving id2 — a broker busy with no other tenant — empty.
    final var phase = singlePhase(result);
    Assertions.assertThat(bootstraps(phase, TENANT_B))
        .singleElement()
        .satisfies(
            op -> {
              Assertions.assertThat(op.partitionId()).isEqualTo(2);
              Assertions.assertThat(op.memberId()).isEqualTo(id0);
            });
    Assertions.assertThat(phase.groupOperations().get(TENANT_B))
        .noneMatch(PartitionJoinOperation.class::isInstance);
  }

  @Test
  void shouldRelocateAnotherTenantsPartitionWhenTheDistributionChanges() {
    // given — the default tenant is scaled from 2 partitions to 3, which shifts every partition
    // sorting after it — here tenant-b's only one — onto a different broker
    final var transformer = scaleTransformer(Optional.of(3), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — tenant-b's partition is relocated to id0 as part of the same change. Distributing
    // every tenant's partitions together is what keeps them balanced; the price is that scaling one
    // tenant can move another's.
    final var phase = singlePhase(result);
    Assertions.assertThat(phase.groupOperations().get(TENANT_B))
        .filteredOn(PartitionJoinOperation.class::isInstance)
        .singleElement()
        .satisfies(
            op -> {
              Assertions.assertThat(((PartitionJoinOperation) op).partitionId()).isEqualTo(1);
              Assertions.assertThat(op.memberId()).isEqualTo(id0);
            });
  }

  @Test
  void shouldRunScaleUpOperationsOnTheClusterConfigurationCoordinator() {
    // given — tenant-b is served by id2 alone
    final var transformer = scaleTransformer(Optional.of(2), Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — the scale-up operations name the cluster configuration coordinator, the lowest live
    // member id, rather than a member picked from the scaled group: they drive that group's engine
    // through a group-scoped broker request instead of acting on a local partition, and every
    // broker registers change appliers for every configured physical tenant.
    final var scaleUpOperations =
        singlePhase(result).groupOperations().get(TENANT_B).stream()
            .filter(ScaleUpOperation.class::isInstance)
            .toList();
    Assertions.assertThat(scaleUpOperations).isNotEmpty();
    Assertions.assertThat(scaleUpOperations)
        .allSatisfy(op -> Assertions.assertThat(op.memberId()).isEqualTo(id0));
  }

  @Test
  void shouldTargetOnlyTheDefaultTenantWhenUnscoped() {
    // given — no physicalTenant parameter: the partition count is not a cluster-wide dimension, so
    // it applies to the default tenant, as it did before tenants could be named at all
    final var transformer = scaleTransformer(Optional.of(3), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — only the default tenant gains a partition
    final var phase = singlePhase(result);
    Assertions.assertThat(bootstraps(phase, DEFAULT_GROUP))
        .singleElement()
        .satisfies(op -> Assertions.assertThat(op.partitionId()).isEqualTo(3));
    Assertions.assertThat(bootstraps(phase, TENANT_B)).isEmpty();
  }

  @Test
  void shouldRejectPartitionCountBelowTheTenantsCurrentCount() {
    // given — partitions can only be scaled up, and the default tenant already has 2
    final var transformer = scaleTransformer(Optional.of(1), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectUnknownPhysicalTenantWhenScaling() {
    // given
    final var transformer = scaleTransformer(Optional.of(3), Optional.of("unknown"));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldRejectBrokerCountCombinedWithPhysicalTenant() {
    // given — brokerCount changes cluster membership, which has no tenant dimension
    final var transformer =
        new ClusterScaleRequestTransformer(
            Optional.of(3),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectReplicationFactorCombinedWithPhysicalTenant() {
    // given — the replication factor is a cluster-wide setting, so it has no tenant dimension
    final var transformer =
        new ClusterScaleRequestTransformer(
            Optional.empty(),
            Optional.empty(),
            Optional.of(2),
            Optional.empty(),
            Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  // -- cluster-wide replication factor (phases()) --

  private ClusterScaleRequestTransformer replicationFactorTransformer(
      final int newReplicationFactor, final Optional<String> zone) {
    return new ClusterScaleRequestTransformer(
        Optional.empty(), Optional.empty(), Optional.of(newReplicationFactor), zone);
  }

  @Test
  void shouldApplyReplicationFactorToEveryPhysicalTenant() {
    // given — every partition of both tenants is currently held by a single broker
    final var transformer = replicationFactorTransformer(2, Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — every partition of every tenant gains a second replica, rather than only the default
    // tenant's, which is what planning against the default group's projection alone produced
    final var phase = singlePhase(result);
    Assertions.assertThat(phase.groupOperations()).containsOnlyKeys(DEFAULT_GROUP, TENANT_B);
    Assertions.assertThat(joins(phase, DEFAULT_GROUP))
        .extracting(PartitionJoinOperation::partitionId)
        .containsExactlyInAnyOrder(1, 2);
    Assertions.assertThat(joins(phase, TENANT_B))
        .extracting(PartitionJoinOperation::partitionId)
        .containsExactly(1);
  }

  @Test
  void shouldRejectReplicationFactorAboveTheNumberOfBrokers() {
    // given — three brokers cannot hold four replicas of a partition
    final var transformer = replicationFactorTransformer(4, Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("Number of brokers [3] is less than the replication factor [4]");
  }

  @Test
  void shouldRedistributeEveryPhysicalTenantWhenChangingTheBrokerCount() {
    // given — the cluster shrinks from three brokers to two. Only broker id2 holds anything that
    // has to move, and what it holds belongs to tenant-b, so the whole partition half of this plan
    // is work the default-group projection could never have produced.
    final var transformer =
        new ClusterScaleRequestTransformer(
            Optional.of(2), Optional.empty(), Optional.empty(), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get()).hasSize(3);
    final var partitionPhase = (PartitionGroupPhase) result.get().get(1);
    Assertions.assertThat(partitionPhase.groupOperations()).containsOnlyKeys(TENANT_B);
    Assertions.assertThat(joins(partitionPhase, TENANT_B))
        .describedAs("tenant-b's partition is placed on a retained broker")
        .extracting(PartitionJoinOperation::memberId)
        .containsExactly(id0);
    Assertions.assertThat(partitionPhase.groupOperations().get(TENANT_B))
        .describedAs("tenant-b gives up the partition on the departing broker")
        .contains(new PartitionLeaveOperation(id2, 1, 1));
    Assertions.assertThat(((GlobalPhase) result.get().get(2)).operations())
        .describedAs("the broker leaves only after the partition work")
        .contains(new MemberLeaveOperation(id2));
  }

  @Test
  void shouldRejectReplicationFactorWithZone() {
    // given — the replication factor of a zone-aware cluster follows from its zone specs
    final var transformer = replicationFactorTransformer(2, Optional.of(ZONE_A));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("/partition-distribution");
  }
}
