/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Every test here calls {@link AdditivePartitionReassigner#reassignPartitions} with {@code
 * targetPartitionIds} consisting of ALL partition ids from ALL groups present in the current
 * configuration (existing ids for every group, plus any new ones) — never a subset that omits a
 * group that already exists. The one exception is a genuinely brand-new cluster with no prior state
 * ({@link #shouldDistributeEvenlyWhenNoExistingPartitions()}), where the full target list and the
 * new-ids list are the same thing by definition.
 */
final class AdditivePartitionReassignerTest {

  private static final String NEW_GROUP = "tenantB";

  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  private final AdditivePartitionReassigner reassigner = new AdditivePartitionReassigner();

  @Test
  void shouldDistributeEvenlyWhenNoExistingPartitions() {
    // given — a fresh cluster with no partition groups yet, so the full target list is all new
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = members(4);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 4);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3);

    // then — every member ends up with the same replica count
    assertThat(assigned).hasSize(4);
    final var replicaCounts = countReplicasPerMember(assigned);
    assertThat(replicaCounts.values()).allMatch(count -> count == 3);
  }

  @Test
  void shouldPlaceNewPartitionsOnLeastLoadedMembersFirst() {
    // given — tenantA loads member 0 twice and members 1 and 2 once each; tenantB already has
    // partition 1 on member 1
    final var tenantAPartitions =
        Set.of(
            PartitionMetadataFixtures.partition(
                "tenantA", 1, Set.of(member(0), member(1)), member(0)),
            PartitionMetadataFixtures.partition(
                "tenantA", 2, Set.of(member(0), member(2)), member(0)));
    final var existingTenantB =
        PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(member(1)), member(1));
    final var configuration =
        configurationWithExistingGroups(
            Map.of("tenantA", tenantAPartitions, NEW_GROUP, Set.of(existingTenantB)));

    // when — the full target list covers every group: tenantA's existing partitions 1-2, plus
    // tenantB's existing partition 1 and new partition 2
    final var targetMembers = members(4);
    final var targetPartitionIds =
        List.of(
            new PartitionId("tenantA", 1),
            new PartitionId("tenantA", 2),
            new PartitionId(NEW_GROUP, 1),
            new PartitionId(NEW_GROUP, 2));
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — every existing partition of both groups is unchanged, and the new tenantB partition 2
    // lands on member 3, the only member with zero load from either group
    assertThat(assigned).containsAll(tenantAPartitions);
    assertThat(assigned).contains(existingTenantB);
    final var placed = findPartition(assigned, NEW_GROUP, 2);
    assertThat(placed.members()).containsExactlyInAnyOrder(member(3), member(2));
  }

  @Test
  void shouldNotCountADisabledTenantsPartitionsAsLoadWhenPlacingNewTenantPartitions() {
    // given — tenantB (enabled) loads member 0 twice; tenantA (about to be disabled) loads member
    // 1 three times, i.e. more heavily than member 0
    final var tenantBPartitions =
        Set.of(
            PartitionMetadataFixtures.partition(
                "tenantB-existing", 1, Set.of(member(0)), member(0)),
            PartitionMetadataFixtures.partition(
                "tenantB-existing", 2, Set.of(member(0)), member(0)));
    final var tenantAPartitions =
        Set.of(
            PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(1)), member(1)),
            PartitionMetadataFixtures.partition("tenantA", 2, Set.of(member(1)), member(1)),
            PartitionMetadataFixtures.partition("tenantA", 3, Set.of(member(1)), member(1)));
    final var configuration =
        configurationWithExistingGroups(
                Map.of(
                    "tenantB-existing", tenantBPartitions,
                    "tenantA", tenantAPartitions))
            .updatePartitionGroupConfig("tenantA", PartitionGroupConfiguration::disable);

    // when — placing one partition for a brand-new tenant, choosing between members 0 and 1
    final var targetMembers = Set.of(member(0), member(1));
    final var targetPartitionIds = new ArrayList<>(sortedPartitionIds("tenantB-existing", 1, 2));
    targetPartitionIds.addAll(sortedPartitionIds("tenantA", 1, 3));
    targetPartitionIds.add(new PartitionId(NEW_GROUP, 1));
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 1);

    // then — with tenantA's (disabled) load excluded, member 1 looks less loaded (0) than member 0
    // (2 from tenantB), so the new partition lands on member 1. Counting tenantA's load would have
    // placed it on member 0 instead, since member 1 would then look more loaded (3 vs 2).
    final var placed = findPartition(assigned, NEW_GROUP, 1);
    assertThat(placed.members()).containsExactly(member(1));
  }

  @Test
  void shouldPassThroughExistingPartitionsOfTheSameGroupUnchanged() {
    // given — tenantB already has partition 1; the full target list is partitions 1 and 2
    final PartitionMetadata existingPartition =
        new PartitionMetadata(
            new PartitionId(NEW_GROUP, 1),
            Set.of(member(0), member(1)),
            Map.of(member(0), 2, member(1), 1),
            2,
            member(0));
    final var configuration =
        configurationWithExistingGroups(Map.of(NEW_GROUP, Set.of(existingPartition)));

    // when
    final var targetMembers = members(3);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 2);
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — partition 1 comes back byte-for-byte identical, only partition 2 is newly placed
    assertThat(assigned).contains(existingPartition);
    assertThat(assigned.stream().map(PartitionMetadata::id))
        .containsExactlyInAnyOrder(new PartitionId(NEW_GROUP, 1), new PartitionId(NEW_GROUP, 2));
  }

  @Test
  void shouldPassThroughEveryOtherGroupsExistingPartitionsUnchanged() {
    // given — tenantA already has partition 1; tenantB already has partition 1
    final PartitionMetadata tenantAPartition =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0), member(1)), member(0));
    final PartitionMetadata existingTenantB =
        PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(member(0), member(1)), member(0));
    final var configuration =
        configurationWithExistingGroups(
            Map.of("tenantA", Set.of(tenantAPartition), NEW_GROUP, Set.of(existingTenantB)));

    // when — the full target list covers both groups: tenantA's existing partition 1, and
    // tenantB's existing partition 1 plus new partition 2
    final var targetMembers = members(3);
    final var targetPartitionIds =
        List.of(
            new PartitionId("tenantA", 1),
            new PartitionId(NEW_GROUP, 1),
            new PartitionId(NEW_GROUP, 2));
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — both groups' existing partitions are unchanged, and only tenantB's new partition 2
    // is newly placed
    assertThat(assigned).contains(tenantAPartition, existingTenantB);
    assertThat(assigned.stream().map(PartitionMetadata::id))
        .containsExactlyInAnyOrder(
            new PartitionId("tenantA", 1),
            new PartitionId(NEW_GROUP, 1),
            new PartitionId(NEW_GROUP, 2));
  }

  @Test
  void shouldProduceDeterministicResultsAcrossRepeatedCalls() {
    // given — tenantA plus tenantB already has partitions 1 and 2
    final PartitionMetadata tenantAPartition =
        PartitionMetadataFixtures.partition(
            "tenantA", 1, Set.of(member(0), member(1), member(2)), member(1));
    final var existingTenantB =
        Set.of(
            PartitionMetadataFixtures.partition(
                NEW_GROUP, 1, Set.of(member(0), member(1)), member(0)),
            PartitionMetadataFixtures.partition(
                NEW_GROUP, 2, Set.of(member(1), member(2)), member(1)));
    final var configuration =
        configurationWithExistingGroups(
            Map.of("tenantA", Set.of(tenantAPartition), NEW_GROUP, existingTenantB));
    final var targetMembers = members(5);
    // full target list: tenantA's existing partition 1, plus tenantB's existing partitions 1-2
    // and new partitions 3-6
    final var targetPartitionIds = new ArrayList<>(sortedPartitionIds(NEW_GROUP, 1, 6));
    targetPartitionIds.add(0, new PartitionId("tenantA", 1));

    // when
    final var first =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3);
    final var second =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3);

    // then
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldPlaceZoneAwareMembersWithCollidingNodeIdxIdenticallyRegardlessOfIterationOrder() {
    // given — "us_0"/"eu_0" share nodeIdx 0, "us_1"/"eu_1" share nodeIdx 1. Two target-member sets
    // with the exact same four elements but opposite insertion/iteration order (LinkedHashSet
    // preserves insertion order, unlike Set.of()). Sorting by nodeIdx alone (rather than the full
    // MemberId.ID_COMPARATOR, which also orders by zone) would leave each colliding pair tied, and
    // Java's stable sort would then preserve whatever relative order they happened to arrive in —
    // making the outcome depend on incidental iteration order instead of the members' actual
    // identities.
    final var usZone0 = zoneMember("us", 0);
    final var euZone0 = zoneMember("eu", 0);
    final var usZone1 = zoneMember("us", 1);
    final var euZone1 = zoneMember("eu", 1);
    final var membersInOneOrder = new LinkedHashSet<>(List.of(usZone0, euZone0, usZone1, euZone1));
    final var membersInReverseOrder =
        new LinkedHashSet<>(List.of(euZone1, usZone1, euZone0, usZone0));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 4);

    // when — reassigning the exact same target members, just enumerated in a different order
    final var first =
        reassigner.reassignPartitions(configuration, membersInOneOrder, targetPartitionIds, 2);
    final var second =
        reassigner.reassignPartitions(configuration, membersInReverseOrder, targetPartitionIds, 2);

    // then — the outcome is identical regardless of iteration order
    assertThat(first).isEqualTo(second);

    // and — all four zone-aware members are treated as distinct and none is silently dropped or
    // conflated with its same-nodeIdx counterpart in the other zone
    final var replicaCounts = countReplicasPerMember(first);
    assertThat(replicaCounts.keySet())
        .containsExactlyInAnyOrder(usZone0, euZone0, usZone1, euZone1);
    assertThat(replicaCounts.values()).allMatch(count -> count == 2);
  }

  @Test
  void shouldOnlyPlaceOnTargetMembers() {
    // given — tenantB already has partition 1 on member 0; the caller only wants members 0 and 1
    // as candidates, even though member 2 exists in the cluster
    final var existingTenantB =
        PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(member(0)), member(0));
    final var configuration =
        configurationWithExistingGroups(Map.of(NEW_GROUP, Set.of(existingTenantB)));
    final var targetMembers = Set.of(member(0), member(1));

    // when — full target list: existing partition 1 plus new partition 2
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 2);
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — member 2 never receives a replica, even though it exists in the cluster
    assertThat(assigned)
        .allSatisfy(metadata -> assertThat(metadata.members()).doesNotContain(member(2)));
  }

  @Test
  void shouldPlaceNewPartitionsAcrossMultipleGroupsInOneCall() {
    // given — tenantA already has partition 1 on member 0
    final PartitionMetadata existingTenantA =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0)), member(0));
    final var configuration =
        configurationWithExistingGroups(Map.of("tenantA", Set.of(existingTenantA)));

    // when — the full target list spans tenantA (existing partition 1 + new partition 2) and a
    // brand-new tenantC (new partition 1), all in one call
    final var targetMembers = members(3);
    final var targetPartitionIds =
        List.of(
            new PartitionId("tenantA", 1), // existing — must pass through unchanged
            new PartitionId("tenantA", 2), // new
            new PartitionId("tenantC", 1)); // new, different group entirely
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 1);

    // then — the existing tenantA partition is untouched, and both new ids (across the two
    // groups) are placed, sharing the same load view so they don't both land on the same member
    assertThat(assigned).contains(existingTenantA);
    assertThat(assigned.stream().map(PartitionMetadata::id))
        .containsExactlyInAnyOrder(
            new PartitionId("tenantA", 1),
            new PartitionId("tenantA", 2),
            new PartitionId("tenantC", 1));
    final var newTenantAPartition = findPartition(assigned, "tenantA", 2);
    final var newTenantCPartition = findPartition(assigned, "tenantC", 1);
    assertThat(newTenantAPartition.members()).isNotEqualTo(newTenantCPartition.members());
  }

  @Test
  void shouldRejectEmptyTargetMembers() {
    // given
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 2);

    // when/then
    assertThatThrownBy(
            () -> reassigner.reassignPartitions(configuration, Set.of(), targetPartitionIds, 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNonPositiveReplicationFactor() {
    // given
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = members(2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 2);

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectTargetMembersSmallerThanReplicationFactor() {
    // given
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = members(2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then — 2 target members can never satisfy RF 3
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectTargetPartitionIdsThatOmitAnExistingIdOfATouchedGroup() {
    // given — tenantB already has partitions 1 and 2
    final var existingTenantB =
        Set.of(
            PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(member(0)), member(0)),
            PartitionMetadataFixtures.partition(NEW_GROUP, 2, Set.of(member(1)), member(1)));
    final var configuration = configurationWithExistingGroups(Map.of(NEW_GROUP, existingTenantB));
    final var targetMembers = members(3);
    // omits tenantB's existing partition 2 — only lists partition 1 (unchanged) and a new one
    final var incompleteTargetPartitionIds =
        List.of(new PartitionId(NEW_GROUP, 1), new PartitionId(NEW_GROUP, 3));

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(
                    configuration, targetMembers, incompleteTargetPartitionIds, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectTargetPartitionIdsThatOmitAnExistingGroupEntirely() {
    // given — tenantA has an existing partition, but the target list only mentions tenantB
    final PartitionMetadata existingTenantA =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(member(0)), member(0));
    final var configuration =
        configurationWithExistingGroups(Map.of("tenantA", Set.of(existingTenantA)));
    final var targetMembers = members(3);
    final var targetPartitionIdsMissingTenantA = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then — removing a whole group is not supported, so this must be rejected rather than
    // silently leaving tenantA untouched in the background
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(
                    configuration, targetMembers, targetPartitionIdsMissingTenantA, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldNotRejectWhenAnUnrelatedGroupHasMoreReplicasThanTheRequestedReplicationFactor() {
    // given — tenantA already has 3 replicas (e.g. from an earlier, independent per-tenant scale
    // up), while tenantB is being provisioned with replicationFactor 1. tenantA is untouched and
    // not being scaled by this call at all
    final PartitionMetadata existingTenantA =
        PartitionMetadataFixtures.partition(
            "tenantA", 1, Set.of(member(0), member(1), member(2)), member(0));
    final var configuration =
        configurationWithExistingGroups(Map.of("tenantA", Set.of(existingTenantA)));
    final var targetMembers = members(3);
    final var targetPartitionIds =
        List.of(new PartitionId("tenantA", 1), new PartitionId(NEW_GROUP, 1));

    // when — this must succeed rather than reject the whole batch just because an unrelated
    // group's current replica count happens to exceed this call's replicationFactor
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 1);

    // then — tenantA's over-replicated partition is passed through completely unchanged
    assertThat(assigned).contains(existingTenantA);
    final var placed = findPartition(assigned, NEW_GROUP, 1);
    assertThat(placed.members()).hasSize(1);
  }

  private CurrentClusterConfiguration configurationWithExistingGroups(
      final Map<String, Set<PartitionMetadata>> existingGroups) {
    final Set<PartitionMetadata> allExisting = new HashSet<>();
    existingGroups.values().forEach(allExisting::addAll);
    final Set<MemberId> members = new HashSet<>();
    allExisting.forEach(metadata -> members.addAll(metadata.members()));
    // ensure the configuration always has at least members 0-4 available as cluster members
    for (int i = 0; i < 5; i++) {
      members.add(member(i));
    }

    final var tenantConfig =
        existingGroups.keySet().stream()
            .collect(Collectors.toMap(group -> group, group -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members, allExisting, tenantConfig, "clusterId");
  }

  private Set<MemberId> members(final int count) {
    final Set<MemberId> members = new HashSet<>();
    for (int i = 0; i < count; i++) {
      members.add(member(i));
    }
    return members;
  }

  private PartitionMetadata findPartition(
      final Set<PartitionMetadata> assigned, final String group, final int number) {
    return assigned.stream()
        .filter(metadata -> metadata.id().equals(new PartitionId(group, number)))
        .findFirst()
        .orElseThrow();
  }

  private Map<MemberId, Integer> countReplicasPerMember(final Set<PartitionMetadata> assigned) {
    final Map<MemberId, Integer> counts = new HashMap<>();
    assigned.forEach(
        metadata -> metadata.members().forEach(member -> counts.merge(member, 1, Integer::sum)));
    return counts;
  }

  private List<PartitionId> sortedPartitionIds(
      final String group, final int fromInclusive, final int toInclusive) {
    final List<PartitionId> ids = new ArrayList<>();
    for (int i = fromInclusive; i <= toInclusive; i++) {
      ids.add(new PartitionId(group, i));
    }
    return ids;
  }

  private MemberId member(final int id) {
    return MemberId.from(String.valueOf(id));
  }

  private MemberId zoneMember(final String zone, final int nodeIdx) {
    return MemberId.from(zone, nodeIdx);
  }
}
