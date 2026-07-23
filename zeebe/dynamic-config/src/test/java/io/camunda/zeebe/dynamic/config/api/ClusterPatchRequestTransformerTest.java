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

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.RoutingStateInitializer;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
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
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class ClusterPatchRequestTransformerTest {

  private static final String TENANT_B = "tenant-b";
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

  @Test
  void shouldRejectIfSameMembersAreAddedAndRemoved() {
    // given
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id0, id2), Set.of(id0, id1), Optional.empty(), Optional.empty(), false);

    // when
    final var result =
        plannedOperations(
            new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor()),
            ClusterConfiguration.init());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectWhenScalingDownPartitions() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(Set.of(), Set.of(), Optional.of(1), Optional.empty(), false);
    final var result =
        plannedOperations(
            new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor()),
            ClusterConfiguration.init());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldScaleUpBrokersWhenPartitionUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(Set.of(id2), Set.of(), Optional.empty(), Optional.empty(), false);
    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id1, id2), getSortedPartitionIds(2), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, getClusterMembers(3), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldScaleDownBrokersWhenPartitionUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(Set.of(), Set.of(id1), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, Set.of(id0), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersWhenPartitionsUnchanged() {
    // given
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    // when
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.empty(), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(2), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2, 2, Set.of(id0, id2), patchRequest, currentTopology, expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitions() {
    // given
    var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));
    currentTopology = new RoutingStateInitializer().modify(currentTopology).join();

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.of(newPartitionCount), Optional.empty(), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 1);

    // then
    applyRequestAndVerifyResultingTopology(
        2,
        newPartitionCount,
        Set.of(id0, id2),
        patchRequest,
        currentTopology,
        expectedDistribution);
  }

  @Test
  void shouldAddAndRemoveBrokersAndAddPartitionsAndChangeReplicationFactor() {
    // given
    var currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));
    currentTopology = new RoutingStateInitializer().modify(currentTopology).join();

    // when
    final int newPartitionCount = 4;
    final var patchRequest =
        new ClusterPatchRequest(
            Set.of(id2), Set.of(id1), Optional.of(newPartitionCount), Optional.of(2), false);

    final var expectedDistribution =
        new RoundRobinPartitionDistributor()
            .distributePartitions(Set.of(id0, id2), getSortedPartitionIds(newPartitionCount), 2);

    // then
    applyRequestAndVerifyResultingTopology(
        2,
        newPartitionCount,
        Set.of(id0, id2),
        patchRequest,
        currentTopology,
        expectedDistribution);
  }

  private void applyRequestAndVerifyResultingTopology(
      final int oldPartitionCount,
      final int partitionCount,
      final Set<MemberId> expectedMembers,
      final ClusterPatchRequest patchRequest,
      final ClusterConfiguration oldClusterTopology,
      final Set<PartitionMetadata> expectedNewDistribution) {

    // when
    final var result =
        plannedOperations(
            new ClusterPatchRequestTransformer(
                patchRequest.membersToAdd(),
                patchRequest.membersToRemove(),
                patchRequest.newPartitionCount(),
                patchRequest.newReplicationFactor()),
            oldClusterTopology);
    assertThat(result).isRight();
    final var operations = result.get();

    // apply operations to generate new topology
    final ClusterConfiguration newTopology =
        TestTopologyChangeSimulator.apply(oldClusterTopology, operations);

    // then
    final var newDistribution = ConfigurationUtil.getPartitionDistributionFrom(newTopology, "temp");
    Assertions.assertThat(newDistribution)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .isEqualTo(expectedNewDistribution);
    Assertions.assertThat(newTopology.members().keySet())
        .describedAs("Expected cluster members")
        .containsExactlyInAnyOrderElementsOf(expectedMembers);
    Assertions.assertThat(newTopology.partitionCount()).isEqualTo(partitionCount);
    if (oldPartitionCount == partitionCount) {
      Assertions.assertThat(operations)
          .allSatisfy(
              op ->
                  Assertions.assertThat(op)
                      .isNotInstanceOfAny(
                          ScaleUpOperation.class, PartitionBootstrapOperation.class));
    } else if (partitionCount > oldPartitionCount) {
      final var scaleUpInstances =
          operations.stream()
              .filter(ScaleUpOperation.class::isInstance)
              .map(Object::getClass)
              .collect(Collectors.toSet());
      Assertions.assertThat(scaleUpInstances)
          .isEqualTo(
              Set.of(
                  ScaleUpOperation.StartPartitionScaleUp.class,
                  ScaleUpOperation.AwaitRedistributionCompletion.class,
                  ScaleUpOperation.AwaitRelocationCompletion.class));
    }
  }

  private List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(id -> new PartitionId("temp", id))
        .collect(Collectors.toList());
  }

  private Set<MemberId> getClusterMembers(final int newClusterSize) {
    return IntStream.range(0, newClusterSize)
        .mapToObj(Integer::toString)
        .map(MemberId::from)
        .collect(Collectors.toSet());
  }

  // -- physicalTenant scoping (phases()) --

  /**
   * Three brokers, replication factor 1, and a placement that already follows the cluster-wide
   * round robin over every tenant's partitions: the default tenant holds partitions 1 and 2 on id0
   * and id1, and tenant-b, whose partition sorts after both of them, holds its single partition on
   * id2. Scaling a tenant here therefore has nothing to relocate, which leaves id2 as the broker a
   * tenant-blind placement would strand.
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
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(2), Optional.empty(), Optional.of(TENANT_B));

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
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(2), Optional.empty(), Optional.of(TENANT_B));

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
  void shouldTargetOnlyTheDefaultTenantWhenUnscoped() {
    // given — no physicalTenant parameter: the partition count is not a cluster-wide dimension, so
    // it applies to the default tenant, as it did before tenants could be named at all
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(3), Optional.empty(), Optional.empty());

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
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(1), Optional.empty(), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectUnknownPhysicalTenantWhenPatching() {
    // given
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(3), Optional.empty(), Optional.of("unknown"));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(NotFound.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldRejectAddingMembersCombinedWithPhysicalTenant() {
    // given — membersToAdd changes cluster membership, which has no tenant dimension
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(id3), Set.of(), Optional.empty(), Optional.empty(), Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectRemovingMembersCombinedWithPhysicalTenant() {
    // given — membersToRemove changes cluster membership, which has no tenant dimension
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(id1), Optional.empty(), Optional.empty(), Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  @Test
  void shouldRejectReplicationFactorCombinedWithPhysicalTenant() {
    // given — the replication factor is a cluster-wide setting, so it has no tenant dimension
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.empty(), Optional.of(2), Optional.of(TENANT_B));

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft().left().isInstanceOf(InvalidRequest.class);
  }

  // -- cluster-wide replication factor (phases()) --

  @Test
  void shouldApplyReplicationFactorToEveryPhysicalTenant() {
    // given — every partition of both tenants is currently held by a single broker
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.empty(), Optional.of(2), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — every partition of every tenant gains a second replica. Planning this against the
    // default group's projection alone, as an unscoped request used to, would have left tenant-b
    // at a replication factor nobody chose for it.
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
  void shouldNotRunScaleUpOperationsForAReplicationFactorChange() {
    // given
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.empty(), Optional.of(2), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — no partition is added, so the engine never has to be told about a new partition count
    // nor awaited for redistribution and relocation
    Assertions.assertThat(singlePhase(result).groupOperations().values())
        .allSatisfy(
            operations ->
                Assertions.assertThat(operations).noneMatch(ScaleUpOperation.class::isInstance));
  }

  @Test
  void shouldApplyReplicationFactorAndPartitionCountTogether() {
    // given — the default tenant is scaled from 2 partitions to 3 and the replication factor from
    // 1 to 2 in one request
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(3), Optional.of(2), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — only the default tenant gains a partition, but both tenants reach the new replication
    // factor: the partition count targets one group, the replication factor spans all of them
    final var phase = singlePhase(result);
    Assertions.assertThat(bootstraps(phase, DEFAULT_GROUP))
        .singleElement()
        .satisfies(op -> Assertions.assertThat(op.partitionId()).isEqualTo(3));
    Assertions.assertThat(bootstraps(phase, TENANT_B)).isEmpty();
    Assertions.assertThat(joins(phase, TENANT_B)).isNotEmpty();
  }

  @Test
  void shouldRejectReplicationFactorAboveTheNumberOfBrokers() {
    // given — three brokers cannot hold four replicas of a partition
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.empty(), Optional.of(4), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("Number of brokers [3] is less than the replication factor [4]");
  }

  @Test
  void shouldRejectReplicationFactorOnZoneAwareCluster() {
    // given — a zone-aware cluster derives its replication factor from its zone specs, so it
    // cannot be set directly
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.empty(), Optional.of(2), Optional.empty());

    // when
    final var result = transformer.phases(zoneAwareTwoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("not supported on zone-aware clusters");
  }

  @Test
  void shouldPlanNothingWhenOnlyThePartitionCountIsGivenAndUnchanged() {
    // given — the default tenant already has the requested 2 partitions, and no replication factor
    // is requested
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(), Optional.of(2), Optional.empty(), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then — a request that asks for nothing plans nothing, rather than rebalancing a placement
    // that may have drifted from what the distributor would compute now
    assertThat(result).isRight();
    Assertions.assertThat(result.get()).isEmpty();
  }

  @Test
  void shouldRedistributeEveryPhysicalTenantWhenRemovingABroker() {
    // given — id2 holds only tenant-b's partition, so the whole partition half of this plan is work
    // the default-group projection could never have produced
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(), Set.of(id2), Optional.empty(), Optional.empty());

    // when
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isRight();
    Assertions.assertThat(result.get()).hasSize(3);
    final var partitionPhase = (PartitionGroupPhase) result.get().get(1);
    Assertions.assertThat(partitionPhase.groupOperations()).containsOnlyKeys(TENANT_B);
    Assertions.assertThat(partitionPhase.groupOperations().get(TENANT_B))
        .describedAs("tenant-b gives up the partition on the departing broker")
        .contains(new PartitionLeaveOperation(id2, 1, 1));
    Assertions.assertThat(((GlobalPhase) result.get().get(2)).operations())
        .describedAs("the broker leaves only after the partition work")
        .contains(new MemberLeaveOperation(id2));
  }

  @Test
  void shouldRejectReplicationFactorOnZoneAwareClusterEvenWhenChangingMembership() {
    // given — a request that both adds a broker and raises the replication factor
    final var transformer =
        new ClusterPatchRequestTransformer(Set.of(id3), Set.of(), Optional.empty(), Optional.of(2));

    // when
    final var result = transformer.phases(zoneAwareTwoTenantCluster());

    // then — answered with the zone-aware rejection, not with whatever the zone-aware distributor
    // raises about the resulting replica sum once a placement is attempted
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("zone-aware");
  }

  @Test
  void shouldRejectAddingAndRemovingTheSameMemberWhenPlanningPhases() {
    // given
    final var transformer =
        new ClusterPatchRequestTransformer(
            Set.of(id3), Set.of(id3), Optional.empty(), Optional.empty());

    // when — the new-model coordinator plans through phases() alone, so the check has to hold there
    final var result = transformer.phases(twoTenantCluster());

    // then
    assertThat(result).isLeft();
    Assertions.assertThat(result.getLeft())
        .isInstanceOf(InvalidRequest.class)
        .hasMessageContaining("Cannot add and remove the same member");
  }

  /** {@link #twoTenantCluster()} with a zone-aware partition distributor. */
  private CurrentClusterConfiguration zoneAwareTwoTenantCluster() {
    final var cluster = twoTenantCluster();
    final var global = cluster.globalConfiguration();
    return new CurrentClusterConfiguration(
        cluster.version(),
        new GlobalConfiguration(
            global.version(),
            global.clusterId(),
            global.members(),
            Optional.of(new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1)))),
            global.pendingChanges(),
            global.lastChange()),
        cluster.partitionGroups(),
        cluster.phasedChangeState());
  }
}
