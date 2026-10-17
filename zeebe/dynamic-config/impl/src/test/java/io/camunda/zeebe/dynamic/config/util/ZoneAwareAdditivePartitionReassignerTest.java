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
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
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
 * Every test here calls {@link ZoneAwareAdditivePartitionReassigner#reassignPartitions} with {@code
 * targetPartitionIds} consisting of ALL partition ids from ALL groups present in the current
 * configuration (existing ids for every group, plus any new ones) — never a subset that omits a
 * group that already exists, mirroring {@code AdditivePartitionReassignerTest}'s convention.
 */
final class ZoneAwareAdditivePartitionReassignerTest {

  private static final String NEW_GROUP = "tenantB";
  private static final String ZONE_A = "zone-a";
  private static final String ZONE_B = "zone-b";
  private static final String ZONE_C = "zone-c";

  private final DynamicPartitionConfig partitionConfig =
      new DynamicPartitionConfig(
          new ExportingConfig(
              ExportingState.EXPORTING,
              Map.of("expA", new ExporterState(1, ExporterState.State.ENABLED, Optional.empty()))));

  @Test
  void shouldPlaceReplicasPerZoneAccordingToZoneSpec() {
    // given — two zones, RF 3 split 2/1, no prior state
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 1, 50)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 3, ZONE_B, 3);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 4);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3);

    // then — every partition has exactly 2 replicas in zone-a and 1 in zone-b
    assertThat(assigned).hasSize(4);
    assigned.forEach(
        metadata -> {
          assertThat(metadata.members().stream().filter(m -> m.isInZone(ZONE_A)).count())
              .isEqualTo(2);
          assertThat(metadata.members().stream().filter(m -> m.isInZone(ZONE_B)).count())
              .isEqualTo(1);
        });
  }

  @Test
  void shouldAlwaysPickPrimaryFromTheHighestPriorityZone() {
    // given — zone-a has the highest priority
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 1000), new ZoneSpec(ZONE_B, 1, 10)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 2, ZONE_B, 2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 6);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then
    assigned.forEach(
        metadata -> assertThat(metadata.getPrimary().orElseThrow().isInZone(ZONE_A)).isTrue());
  }

  @Test
  void shouldAssignPrimaryReplicationFactorPriorityAndDecreaseByZonePriorityOrder() {
    // given — zone-a (priority 1000, 2 replicas) then zone-b (priority 10, 1 replica), RF 3
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 2, 1000), new ZoneSpec(ZONE_B, 1, 10)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 2, ZONE_B, 2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3);

    // then — primary has priority 3, its zone-a zone-mate has 2, and zone-b's member has 1
    final var partition = assigned.iterator().next();
    final var primary = partition.getPrimary().orElseThrow();
    assertThat(partition.getPriority(primary)).isEqualTo(3);
    final var zoneAFollower =
        partition.members().stream()
            .filter(m -> m.isInZone(ZONE_A) && !m.equals(primary))
            .findFirst()
            .orElseThrow();
    assertThat(partition.getPriority(zoneAFollower)).isEqualTo(2);
    final var zoneBMember =
        partition.members().stream().filter(m -> m.isInZone(ZONE_B)).findFirst().orElseThrow();
    assertThat(partition.getPriority(zoneBMember)).isEqualTo(1);
  }

  @Test
  void shouldSpreadPrimariesAcrossZonesTiedForHighestPriority() {
    // given — zone-a and zone-b are tied for the highest priority
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 1, 100)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 2, ZONE_B, 2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 8);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — leadership is balanced across zone-a and zone-b, not always the same zone
    final Map<String, Integer> leadersByZone = new HashMap<>();
    assigned.forEach(
        metadata -> {
          final var primary = metadata.getPrimary().orElseThrow();
          leadersByZone.merge(primary.zone(), 1, Integer::sum);
        });
    assertThat(leadersByZone.get(ZONE_A)).isEqualTo(4);
    assertThat(leadersByZone.get(ZONE_B)).isEqualTo(4);
  }

  @Test
  void shouldRotateWhichFollowerReceivesTheSecondHighestPriorityAcrossPartitions() {
    // given — zone-a has exactly 3 members and needs all 3 as replicas every time, so which
    // member is selected never varies across partitions — only the RELATIVE ORDER among them
    // (who's primary, who's 2nd/3rd priority) can. Sorting followers by plain MemberId order would
    // always hand the 2nd-highest priority to the same lower-id member; this asserts it rotates.
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 3, 100), new ZoneSpec(ZONE_B, 1, 10)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 3, ZONE_B, 1);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 4);

    // when
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 4);

    // then — the member receiving the second-highest priority (replicationFactor - 1, i.e. 3) is
    // not always the same one across partitions
    final Set<MemberId> secondPriorityMembers = new HashSet<>();
    assigned.forEach(
        metadata ->
            metadata.members().stream()
                .filter(m -> metadata.getPriority(m) == 3)
                .forEach(secondPriorityMembers::add));
    assertThat(secondPriorityMembers.size()).isGreaterThan(1);
  }

  @Test
  void shouldProduceDeterministicResultsRegardlessOfTargetMembersIterationOrder() {
    // given — the same four zoned members, enumerated via two LinkedHashSets in opposite order
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 2, 100), new ZoneSpec(ZONE_B, 1, 50)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var zoneA0 = MemberId.from(ZONE_A, 0);
    final var zoneA1 = MemberId.from(ZONE_A, 1);
    final var zoneA2 = MemberId.from(ZONE_A, 2);
    final var zoneB0 = MemberId.from(ZONE_B, 0);
    final var membersInOneOrder = new LinkedHashSet<>(List.of(zoneA0, zoneA1, zoneA2, zoneB0));
    final var membersInReverseOrder = new LinkedHashSet<>(List.of(zoneB0, zoneA2, zoneA1, zoneA0));
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 4);

    // when — reassigning the exact same target members, just enumerated in a different order
    final var first =
        reassigner.reassignPartitions(configuration, membersInOneOrder, targetPartitionIds, 3);
    final var second =
        reassigner.reassignPartitions(configuration, membersInReverseOrder, targetPartitionIds, 3);

    // then — the outcome is identical regardless of iteration order
    assertThat(first).isEqualTo(second);
  }

  @Test
  void shouldNotMoveExistingPartitionsWhenAddingNewOnes() {
    // given — tenantB already has partition 1 placed across both zones
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 1, 50)));
    final var zoneA0 = MemberId.from(ZONE_A, 0);
    final var zoneB0 = MemberId.from(ZONE_B, 0);
    final var existingTenantB =
        PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(zoneA0, zoneB0), zoneA0);
    final var configuration =
        configurationWithExistingGroups(Map.of(NEW_GROUP, Set.of(existingTenantB)));
    final var targetMembers = zoneMembers(ZONE_A, 3, ZONE_B, 3);

    // when — the full target list covers the existing partition plus a new one
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 2);
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — partition 1 is byte-for-byte unchanged
    assertThat(assigned).contains(existingTenantB);
  }

  @Test
  void shouldNotCountADisabledTenantsPartitionsAsLoadWhenPlacingNewTenantPartitions() {
    // given — tenantB-existing (enabled) loads zone-a's member 0 once; tenantA (about to be
    // disabled) loads zone-a's member 1 twice, i.e. more heavily
    final var zoneA0 = MemberId.from(ZONE_A, 0);
    final var zoneA1 = MemberId.from(ZONE_A, 1);
    final var zoneB0 = MemberId.from(ZONE_B, 0);
    final var tenantBPartitions =
        Set.of(
            PartitionMetadataFixtures.partition(
                "tenantB-existing", 1, Set.of(zoneA0, zoneB0), zoneA0));
    final var tenantAPartitions =
        Set.of(
            PartitionMetadataFixtures.partition("tenantA", 1, Set.of(zoneA1, zoneB0), zoneA1),
            PartitionMetadataFixtures.partition("tenantA", 2, Set.of(zoneA1, zoneB0), zoneA1));
    final var configuration =
        configurationWithExistingGroups(
                Map.of(
                    "tenantB-existing", tenantBPartitions,
                    "tenantA", tenantAPartitions))
            .updatePartitionGroupConfig(
                "tenantA",
                io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration::disable);
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 1, 50)));

    // when — placing one partition for a brand-new tenant, choosing zone-a's member
    final var targetMembers = Set.of(zoneA0, zoneA1, zoneB0);
    final var targetPartitionIds = new ArrayList<>(sortedPartitionIds("tenantB-existing", 1, 1));
    targetPartitionIds.addAll(sortedPartitionIds("tenantA", 1, 2));
    targetPartitionIds.add(new PartitionId(NEW_GROUP, 1));
    final var assigned =
        reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2);

    // then — with tenantA's (disabled) load excluded, zone-a's member 1 looks less loaded (0)
    // than member 0 (1 from tenantB-existing), so the new partition's zone-a replica lands there
    final var placed = findPartition(assigned, NEW_GROUP, 1);
    assertThat(placed.members()).contains(zoneA1);
  }

  @Test
  void shouldRejectBareMembers() {
    // given
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(List.of(new ZoneSpec(ZONE_A, 2, 100)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = Set.of(MemberId.from(ZONE_A, 0), MemberId.from("1"));
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectUnknownZoneMembers() {
    // given — the target members include a zone that isn't part of the configured zones
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(List.of(new ZoneSpec(ZONE_A, 2, 100)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers =
        Set.of(MemberId.from(ZONE_A, 0), MemberId.from(ZONE_A, 1), MemberId.from(ZONE_C, 0));
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 2))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectWhenAZoneHasTooFewBrokers() {
    // given — zone-b needs 2 replicas but only has 1 broker
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 2, 50)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 2, ZONE_B, 1);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectWhenReplicaSumDoesNotMatchReplicationFactor() {
    // given — zone specs sum to 2 replicas but replicationFactor 3 is requested
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(
            List.of(new ZoneSpec(ZONE_A, 1, 100), new ZoneSpec(ZONE_B, 1, 50)));
    final var configuration = configurationWithExistingGroups(Map.of());
    final var targetMembers = zoneMembers(ZONE_A, 2, ZONE_B, 2);
    final var targetPartitionIds = sortedPartitionIds(NEW_GROUP, 1, 1);

    // when/then
    assertThatThrownBy(
            () ->
                reassigner.reassignPartitions(configuration, targetMembers, targetPartitionIds, 3))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldRejectTargetPartitionIdsThatOmitAnExistingIdOfATouchedGroup() {
    // given — tenantB already has partitions 1 and 2
    final var zoneA0 = MemberId.from(ZONE_A, 0);
    final var existingTenantB =
        Set.of(
            PartitionMetadataFixtures.partition(NEW_GROUP, 1, Set.of(zoneA0), zoneA0),
            PartitionMetadataFixtures.partition(NEW_GROUP, 2, Set.of(zoneA0), zoneA0));
    final var configuration = configurationWithExistingGroups(Map.of(NEW_GROUP, existingTenantB));
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(List.of(new ZoneSpec(ZONE_A, 1, 100)));
    final var targetMembers = zoneMembers(ZONE_A, 3, ZONE_B, 0);
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
  void shouldNotRejectWhenAnUnrelatedGroupHasMoreReplicasThanTheRequestedReplicationFactor() {
    // given — tenantA already has 3 replicas in zone-a (e.g. from an earlier, independent
    // per-tenant scale up), while tenantB (NEW_GROUP) is being provisioned with replicationFactor
    // 1. tenantA is untouched and not being scaled by this call at all
    final var zoneA0 = MemberId.from(ZONE_A, 0);
    final var zoneA1 = MemberId.from(ZONE_A, 1);
    final var zoneA2 = MemberId.from(ZONE_A, 2);
    final var existingTenantA =
        PartitionMetadataFixtures.partition("tenantA", 1, Set.of(zoneA0, zoneA1, zoneA2), zoneA0);
    final var configuration =
        configurationWithExistingGroups(Map.of("tenantA", Set.of(existingTenantA)));
    final var reassigner =
        new ZoneAwareAdditivePartitionReassigner(List.of(new ZoneSpec(ZONE_A, 1, 100)));
    final var targetMembers = zoneMembers(ZONE_A, 3, ZONE_B, 0);
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
    final Set<MemberId> members =
        new HashSet<>(allExisting.stream().flatMap(m -> m.members().stream()).toList());
    // ensure the configuration always has at least a few zoned members available
    members.addAll(zoneMembers(ZONE_A, 5, ZONE_B, 5));

    final var tenantConfig =
        existingGroups.keySet().stream()
            .collect(Collectors.toMap(group -> group, group -> partitionConfig));
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        members, allExisting, tenantConfig, "clusterId");
  }

  private Set<MemberId> zoneMembers(
      final String zoneA, final int countA, final String zoneB, final int countB) {
    final Set<MemberId> members = new HashSet<>();
    for (int i = 0; i < countA; i++) {
      members.add(MemberId.from(zoneA, i));
    }
    for (int i = 0; i < countB; i++) {
      members.add(MemberId.from(zoneB, i));
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

  private List<PartitionId> sortedPartitionIds(
      final String group, final int fromInclusive, final int toInclusive) {
    final List<PartitionId> ids = new ArrayList<>();
    for (int i = fromInclusive; i <= toInclusive; i++) {
      ids.add(new PartitionId(group, i));
    }
    return ids;
  }
}
