/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.accumulateLoad;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.selectNewPartitionMembers;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.validateExistingPartitionsAreNotRemoved;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.validateTargetMembers;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A {@link PartitionReassigner} for the case where only new partitions are being added to a
 * zone-aware cluster — the zone-aware counterpart of {@link AdditivePartitionReassigner}. Just like
 * that implementation, {@code targetMembers} and {@code replicationFactor} must be unchanged from
 * every affected group's current state, every id already present in its group's current
 * distribution is passed through unchanged, and only ids that don't exist yet are placed.
 *
 * <p>Each new partition's replicas are placed to satisfy the configured {@link ZoneSpec}s exactly:
 * for every zone, {@link ZoneSpec#numberOfReplicas()} replicas are placed on that zone's
 * least-loaded members (by the same replica-count-then-leader-count comparator {@link
 * AdditivePartitionReassigner} uses, but restricted to that zone's candidates), updating the
 * in-memory load view after every placement so a batch of new partitions — even across different
 * groups — is spread evenly both within and across zones.
 *
 * <p>The primary is always a member of one of the zones tied for the highest configured {@link
 * ZoneSpec#priority()} — matching {@link ZoneAwarePartitionDistributor}'s "highest-priority zone is
 * the preferred leader location" guarantee. When a single zone holds the highest priority, every
 * new partition's primary comes from that zone. When several zones tie for the highest priority,
 * primary selection is spread across those tied zones by picking whichever tied-zone member
 * currently has the lowest leader count, so leadership balances across the tied zones the same way
 * {@link ZoneAwarePartitionDistributor}'s round-robin fallback would for an all-equal-priority
 * configuration — without moving any existing partition to get there. Raft election priorities for
 * a new partition are then assigned zone-by-zone from {@code replicationFactor} down to {@code 1},
 * starting with the chosen primary's own zone (so its zone-mates are next in the failover order),
 * then the remaining zones in priority order — mirroring {@link ZoneAwarePartitionDistributor}.
 * Within a zone, followers are ordered by their current replica/leader load rather than by {@link
 * MemberId} — the same load-based order {@link
 * PartitionReassignmentSupport#selectNewPartitionMembers} already produced when picking that zone's
 * replicas — so which follower gets the 2nd-highest (immediate failover) priority rotates across
 * partitions instead of always landing on the same lower-id member.
 *
 * <p>This implementation never moves or otherwise touches an existing partition, never adjusts an
 * existing partition's replication factor, and rejects a bare (not-yet-zoned) target member
 * outright rather than falling back to a distributor that would move existing partitions — a zone
 * migration must have completed on every broker before new physical tenants can be provisioned onto
 * a zone-aware cluster this way.
 *
 * <p><b>Known trade-off:</b> because existing partitions are never touched and a new partition's
 * per-zone replica set is fixed once chosen, leader-count balance across members is a greedy,
 * per-partition choice rather than a globally corrected one — unlike {@link
 * AdditivePartitionReassigner}, which runs a fix-up pass ({@link
 * PartitionReassignmentSupport#rebalanceLeaders}) across already-placed new partitions afterward.
 * Such a fix-up cannot be reused as-is here because it would be free to move a partition's primary
 * to any co-replica regardless of zone, which could hand leadership to a lower-priority zone; a
 * zone-restricted variant was judged unnecessary complexity for now.
 *
 * <p>Removing a partition or an entire group is not supported: {@code targetPartitionIds} must
 * include every existing partition id of every existing group, not just the ones being changed —
 * see {@link PartitionReassignmentSupport#validateExistingPartitionsAreNotRemoved}. This is a
 * deliberate change from the previous zone-aware provisioning path, which delegated to a
 * from-scratch {@link ZoneAwarePartitionDistributor} call with no notion of "current state" and
 * therefore never enforced this. An unrelated group's current replica count exceeding {@code
 * replicationFactor} is not rejected, however — another physical tenant may legitimately be mid-way
 * through its own, independent scaling operation when a new tenant is provisioned, which has no
 * bearing on this call: it never touches any group but the new one(s) being added.
 *
 * <p>Example with 2 zones (zone-a prio=1000 x 2 replicas/4 brokers, zone-b prio=500 x 2 replicas/4
 * brokers), RF=4. {@code foo}/{@code bar} are the cluster's original tenants, placed at bootstrap
 * by {@link ZoneAwarePartitionDistributor}; {@code alpha} is provisioned afterward by this
 * reassigner and lands on the least-loaded remaining brokers per zone, without moving {@code foo}'s
 * or {@code bar}'s replicas:
 *
 * <pre>
 * +-----------+----------+----------+----------+----------+----------+----------+----------+----------+
 * | Partition | zone-a_0 | zone-a_1 | zone-a_2 | zone-a_3 | zone-b_0 | zone-b_1 | zone-b_2 | zone-b_3 |
 * +-----------+----------+----------+----------+----------+----------+----------+----------+----------+
 * | bar-1     |    4     |    3     |          |          |    2     |    1     |          |          |
 * | bar-2     |          |    4     |    3     |          |          |    2     |    1     |          |
 * | bar-3     |          |          |    4     |    3     |          |          |    2     |    1     |
 * | foo-1     |    3     |          |          |    4     |    1     |          |          |    2     |
 * | foo-2     |    4     |    3     |          |          |    2     |    1     |          |          |
 * | foo-3     |          |    4     |    3     |          |          |    2     |    1     |          |
 * | alpha-1   |          |          |    4     |    3     |    1     |          |          |    2     |
 * | alpha-2   |    3     |          |          |    4     |          |          |    2     |    1     |
 * | alpha-3   |    4     |    3     |          |          |    2     |    1     |          |          |
 * +-----------+----------+----------+----------+----------+----------+----------+----------+----------+
 * </pre>
 *
 * (Numbers are Raft priorities; the member with priority == RF is the preferred leader.)
 */
public final class ZoneAwareAdditivePartitionReassigner implements PartitionReassigner {

  private final List<ZoneSpec> zoneSpecs;
  private final List<ZoneSpec> zoneSpecsByPriority;

  /**
   * @param zoneSpecs the zone specifications. May be in any order; sorted internally by {@link
   *     ZoneSpec#priority()} descending so the highest-priority zone(s) are preferred for new
   *     partitions' primaries.
   */
  public ZoneAwareAdditivePartitionReassigner(final List<ZoneSpec> zoneSpecs) {
    this.zoneSpecs = List.copyOf(zoneSpecs);
    ZoneSpecValidation.validateZoneSpecs(this.zoneSpecs);
    zoneSpecsByPriority =
        this.zoneSpecs.stream()
            .sorted(Comparator.comparingInt(ZoneSpec::priority).reversed())
            .toList();
  }

  @Override
  public Set<PartitionMetadata> reassignPartitions(
      final CurrentClusterConfiguration currentConfiguration,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds,
      final int replicationFactor) {

    // validation
    validateTargetMembers(targetMembers, replicationFactor);
    ZoneSpecValidation.validateReplicaSum(zoneSpecs, replicationFactor);
    ZoneSpecValidation.validateNoBareMembers(targetMembers);
    ZoneSpecValidation.validateKnownZonedMembers(zoneSpecs, targetMembers);
    ZoneSpecValidation.validateZoneHasSufficientBrokers(zoneSpecs, targetMembers);

    final Map<String, Set<PartitionMetadata>> distributionByGroup =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(currentConfiguration);
    validateExistingPartitionsAreNotRemoved(distributionByGroup, targetPartitionIds);

    final Set<String> targetGroups =
        targetPartitionIds.stream().map(PartitionId::group).collect(Collectors.toSet());
    final Map<PartitionId, PartitionMetadata> currentById =
        targetGroups.stream()
            .flatMap(group -> distributionByGroup.getOrDefault(group, Set.of()).stream())
            .collect(Collectors.toMap(PartitionMetadata::id, Function.identity()));

    final Map<String, List<MemberId>> membersByZone =
        zoneSpecs.stream()
            .collect(
                Collectors.toMap(
                    ZoneSpec::name,
                    spec -> targetMembers.stream().filter(m -> m.isInZone(spec.name())).toList()));

    final List<PartitionId> sortedPartitionIds =
        targetPartitionIds.stream().distinct().sorted().toList();
    final Set<String> activeGroupIds = currentConfiguration.activePartitionGroups().keySet();

    final Map<MemberId, Integer> replicaCountByMember = new HashMap<>();
    final Map<MemberId, Integer> leaderCountByMember = new HashMap<>();
    // A disabled tenant's partitions are not actually running on any broker, so they must not
    // count as load when deciding where to place a new tenant's partitions — but they still need
    // to appear in distributionByGroup/currentById so they are correctly left untouched rather
    // than treated as removed.
    distributionByGroup.entrySet().stream()
        .filter(entry -> activeGroupIds.contains(entry.getKey()))
        .forEach(
            entry -> accumulateLoad(entry.getValue(), replicaCountByMember, leaderCountByMember));

    final Set<PartitionMetadata> result = new HashSet<>();
    for (final PartitionId id : sortedPartitionIds) {
      final var existing = currentById.get(id);
      if (existing != null) {
        // existing partitions are unchanged, add them as they are.
        result.add(existing);
        continue;
      }
      result.add(
          placeNewPartition(
              id, replicationFactor, membersByZone, replicaCountByMember, leaderCountByMember));
    }
    return result;
  }

  private PartitionMetadata placeNewPartition(
      final PartitionId id,
      final int replicationFactor,
      final Map<String, List<MemberId>> membersByZone,
      final Map<MemberId, Integer> replicaCountByMember,
      final Map<MemberId, Integer> leaderCountByMember) {
    final Map<String, List<MemberId>> selectedMembersByZone = new HashMap<>();
    for (final var spec : zoneSpecsByPriority) {
      final var placement =
          selectNewPartitionMembers(
              membersByZone.get(spec.name()),
              spec.numberOfReplicas(),
              replicaCountByMember,
              leaderCountByMember);
      selectedMembersByZone.put(spec.name(), placement.members());
      placement.members().forEach(member -> replicaCountByMember.merge(member, 1, Integer::sum));
    }

    final int topPriority = zoneSpecsByPriority.getFirst().priority();
    final List<ZoneSpec> topZones =
        zoneSpecsByPriority.stream().filter(spec -> spec.priority() == topPriority).toList();
    final var primaryCandidate =
        topZones.stream()
            .flatMap(
                spec ->
                    selectedMembersByZone.get(spec.name()).stream()
                        .map(member -> Map.entry(member, spec.name())))
            .min(
                Comparator.<Map.Entry<MemberId, String>>comparingInt(
                        entry -> leaderCountByMember.getOrDefault(entry.getKey(), 0))
                    .thenComparing(Map.Entry::getKey, MemberId.ID_COMPARATOR))
            .orElseThrow();
    final var primary = primaryCandidate.getKey();
    final var primaryZoneName = primaryCandidate.getValue();
    leaderCountByMember.merge(primary, 1, Integer::sum);

    final List<ZoneSpec> zoneOrder = new ArrayList<>(zoneSpecsByPriority.size());
    zoneSpecsByPriority.stream()
        .filter(spec -> spec.name().equals(primaryZoneName))
        .forEach(zoneOrder::add);
    zoneSpecsByPriority.stream()
        .filter(spec -> !spec.name().equals(primaryZoneName))
        .forEach(zoneOrder::add);

    final Map<MemberId, Integer> priorityMap = new HashMap<>();
    final Set<MemberId> allMembers = new HashSet<>();
    int priorityCounter = replicationFactor;
    for (final var spec : zoneOrder) {
      // selectedMembersByZone's list is already ordered by selectNewPartitionMembers's
      // replica-count-then-leader-count comparator, which evolves as more partitions are placed —
      // preserving that order (rather than re-sorting by MemberId) is what spreads the 2nd+
      // Raft-priority slot across a zone's members over successive partitions instead of always
      // handing it to the same lower-id member.
      final var zoneMembers = new ArrayList<>(selectedMembersByZone.get(spec.name()));
      zoneMembers.remove(primary);
      final List<MemberId> orderedZoneMembers =
          spec.name().equals(primaryZoneName)
              ? Stream.concat(Stream.of(primary), zoneMembers.stream()).toList()
              : zoneMembers;
      for (final var member : orderedZoneMembers) {
        priorityMap.put(member, priorityCounter--);
        allMembers.add(member);
      }
    }

    return new PartitionMetadata(
        id, Set.copyOf(allMembers), Map.copyOf(priorityMap), replicationFactor, primary);
  }
}
