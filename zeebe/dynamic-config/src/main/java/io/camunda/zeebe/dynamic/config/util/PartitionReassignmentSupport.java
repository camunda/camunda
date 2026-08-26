/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/** Shared helpers used by {@link PartitionReassigner} implementations. */
public final class PartitionReassignmentSupport {

  private PartitionReassignmentSupport() {}

  /**
   * Adds the replica/leader counts contributed by {@code partitions} into the given maps.
   *
   * <p>Known limitation: {@code partitions} comes from {@link
   * ConfigurationUtil#getPartitionDistributionPerPhysicalTenant}, which only counts partitions in
   * {@code ACTIVE}/{@code LEAVING}/{@code RECOVERING}/{@code LEARNER} state and excludes {@code
   * JOINING}. A broker that is mid-join for a partition of some other, untouched group therefore
   * looks under-loaded here relative to its true near-future load, and could receive more new
   * replicas than it should as a result.
   */
  static void accumulateLoad(
      final Set<PartitionMetadata> partitions,
      final Map<MemberId, Integer> replicaCountByMember,
      final Map<MemberId, Integer> leaderCountByMember) {
    partitions.forEach(
        metadata -> {
          metadata.members().forEach(member -> replicaCountByMember.merge(member, 1, Integer::sum));
          metadata
              .getPrimary()
              .ifPresent(primary -> leaderCountByMember.merge(primary, 1, Integer::sum));
        });
  }

  /**
   * Orders members ascending by replica count, then leader count, then {@link
   * MemberId#ID_COMPARATOR} — the least-loaded, most-deterministic member sorts first. {@code
   * nodeIdx} alone is not a total order once zoned members are involved (the same {@code nodeIdx}
   * can occur in more than one zone), so {@code ID_COMPARATOR} — which additionally orders by zone
   * — is used as the final tie-breaker instead.
   */
  static Comparator<MemberId> loadComparator(
      final Map<MemberId, Integer> replicaCountByMember,
      final Map<MemberId, Integer> leaderCountByMember) {
    return Comparator.comparingInt(
            (MemberId member) -> replicaCountByMember.getOrDefault(member, 0))
        .thenComparingInt(member -> leaderCountByMember.getOrDefault(member, 0))
        .thenComparing(MemberId.ID_COMPARATOR);
  }

  /**
   * Picks the {@code replicaSlotCount} members for a brand-new partition by {@link
   * #loadComparator}, then the primary as the first (least-loaded) member of that selection.
   * Leader-count balance is not this method's job — replica-count selection alone can leave leader
   * count skewed (e.g. under a small cluster with a high replication factor, where almost every
   * member is a replica of almost every partition anyway, so replica count alone rarely
   * discriminates in favor of whoever is lightest on leadership) — see {@link #rebalanceLeaders}
   * for the pass that fixes that up afterward without touching any replica set.
   *
   * @param candidates the members eligible for this partition, in any order
   */
  static NewPartitionPlacement selectNewPartitionMembers(
      final List<MemberId> candidates,
      final int replicaSlotCount,
      final Map<MemberId, Integer> replicaCountByMember,
      final Map<MemberId, Integer> leaderCountByMember) {
    final List<MemberId> members =
        candidates.stream()
            .sorted(loadComparator(replicaCountByMember, leaderCountByMember))
            .limit(replicaSlotCount)
            .toList();
    return new NewPartitionPlacement(members, members.get(0));
  }

  /**
   * Fixes up leader-count balance across {@code eligiblePartitionIds} by reassigning which existing
   * replica is primary — never moving a replica, never touching a partition outside the eligible
   * set. A direct swap (busiest hands leadership straight to quietest) only works when they already
   * co-occur on some eligible partition; in general they don't, so each iteration instead searches
   * for a <i>chain</i> of primary reassignments — partition A's primary changes from the busiest
   * member to some member M, which is currently primary of a different partition B, whose primary
   * changes to M2, and so on — that ultimately moves one unit of leadership from the busiest member
   * to some sufficiently-quiet member several hops away, with every intermediate member's leader
   * count unchanged (it loses a primary role but gains one, net zero). This is a standard
   * augmenting-path search over the graph where an edge from member X to member Y exists for every
   * eligible partition X currently leads with Y as a fellow replica.
   *
   * <p>Stops once every target member's leader count is within one of every other's, or once no
   * augmenting path can be found (the busiest member cannot reach any sufficiently quiet member
   * through any chain of eligible partitions).
   *
   * <p>{@code eligiblePartitionIds} lets each caller scope which partitions may have their primary
   * changed — e.g. {@link AdditivePartitionReassigner} restricts this to newly placed partitions
   * only, since it must never touch an existing partition at all.
   */
  static void rebalanceLeaders(
      final List<PartitionId> eligiblePartitionIds,
      final Map<PartitionId, Set<MemberId>> membersByPartition,
      final Map<PartitionId, MemberId> primaryByPartition,
      final List<MemberId> targetMembers,
      final Map<MemberId, Integer> leaderCountByMember) {
    if (targetMembers.size() < 2 || eligiblePartitionIds.isEmpty()) {
      return;
    }
    final int maxIterations = targetMembers.size() * eligiblePartitionIds.size() + 1;
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      final int max = maxLeaderCount(targetMembers, leaderCountByMember);
      final int min = minLeaderCount(targetMembers, leaderCountByMember);
      if (max - min <= 1) {
        return;
      }
      final boolean progressed =
          transferLeadershipFromABusiestMember(
              eligiblePartitionIds,
              membersByPartition,
              primaryByPartition,
              targetMembers,
              leaderCountByMember,
              max);
      if (!progressed) {
        return; // no busiest candidate has an augmenting path — no further progress possible
      }
    }
  }

  private static int maxLeaderCount(
      final List<MemberId> targetMembers, final Map<MemberId, Integer> leaderCountByMember) {
    return targetMembers.stream()
        .mapToInt(member -> leaderCountByMember.getOrDefault(member, 0))
        .max()
        .orElseThrow();
  }

  private static int minLeaderCount(
      final List<MemberId> targetMembers, final Map<MemberId, Integer> leaderCountByMember) {
    return targetMembers.stream()
        .mapToInt(member -> leaderCountByMember.getOrDefault(member, 0))
        .min()
        .orElseThrow();
  }

  /**
   * Tries every member tied for busiest (leader count == {@code max}) as a transfer source, in
   * turn, until one succeeds. Any member tied for busiest is a candidate source: one of them might
   * have no augmenting path to a quiet-enough member while another does (e.g. two members are
   * equally the most leader-loaded, but only one of them happens to share a partition with a member
   * that can pass leadership further along), so every tied candidate is tried before giving up.
   *
   * @return {@code true} if a transfer was applied (leadership moved from some busiest candidate to
   *     a sufficiently quiet member), {@code false} if none of the tied candidates has an
   *     augmenting path
   */
  private static boolean transferLeadershipFromABusiestMember(
      final List<PartitionId> eligiblePartitionIds,
      final Map<PartitionId, Set<MemberId>> membersByPartition,
      final Map<PartitionId, MemberId> primaryByPartition,
      final List<MemberId> targetMembers,
      final Map<MemberId, Integer> leaderCountByMember,
      final int max) {
    final List<MemberId> busiestCandidates =
        targetMembers.stream()
            .filter(member -> leaderCountByMember.getOrDefault(member, 0) == max)
            .toList();
    // A transfer only counts as progress if the target ends up strictly better off than the
    // busiest member did before the transfer — otherwise a chain could just relabel who's
    // "busiest" every iteration without ever converging.
    final int threshold = max - 2;
    for (final MemberId busiest : busiestCandidates) {
      final var path =
          findAugmentingPath(
              busiest,
              threshold,
              eligiblePartitionIds,
              membersByPartition,
              primaryByPartition,
              leaderCountByMember);
      if (path != null) {
        applyAugmentingPath(path, primaryByPartition, leaderCountByMember);
        return true;
      }
    }
    return false;
  }

  /**
   * Searches for a <i>chain</i> of primary reassignments — partition A's primary changes from
   * {@code busiest} to some member M, which is currently primary of a different partition B, whose
   * primary changes to M2, and so on — that ultimately moves one unit of leadership from {@code
   * busiest} to some member whose leader count is already at or below {@code threshold}, with every
   * intermediate member's leader count unchanged (it loses a primary role but gains one, net zero).
   * This is a standard augmenting-path search over the graph where an edge from member X to member
   * Y exists for every eligible partition X currently leads with Y as a fellow replica.
   *
   * @return the found path, or {@code null} if {@code busiest} cannot reach any sufficiently quiet
   *     member through any chain of eligible partitions
   */
  private static AugmentingPath findAugmentingPath(
      final MemberId busiest,
      final int threshold,
      final List<PartitionId> eligiblePartitionIds,
      final Map<PartitionId, Set<MemberId>> membersByPartition,
      final Map<PartitionId, MemberId> primaryByPartition,
      final Map<MemberId, Integer> leaderCountByMember) {
    final Map<MemberId, MemberId> cameFromMember = new HashMap<>();
    final Map<MemberId, PartitionId> cameFromPartition = new HashMap<>();
    final Set<MemberId> visited = new HashSet<>();
    final Set<PartitionId> usedPartitions = new HashSet<>();
    final Queue<MemberId> queue = new ArrayDeque<>();
    visited.add(busiest);
    queue.add(busiest);
    while (!queue.isEmpty()) {
      final MemberId current = queue.poll();
      for (final var id : eligiblePartitionIds) {
        if (usedPartitions.contains(id) || !current.equals(primaryByPartition.get(id))) {
          continue;
        }
        for (final MemberId next : membersByPartition.get(id)) {
          if (next.equals(current) || visited.contains(next)) {
            continue;
          }
          visited.add(next);
          usedPartitions.add(id);
          cameFromMember.put(next, current);
          cameFromPartition.put(next, id);
          if (leaderCountByMember.getOrDefault(next, 0) <= threshold) {
            return new AugmentingPath(busiest, next, cameFromMember, cameFromPartition);
          }
          queue.add(next);
        }
      }
    }
    return null; // no augmenting path found
  }

  /**
   * Replays a found {@link AugmentingPath}'s chain of primary reassignments and updates leader
   * counts.
   */
  private static void applyAugmentingPath(
      final AugmentingPath path,
      final Map<PartitionId, MemberId> primaryByPartition,
      final Map<MemberId, Integer> leaderCountByMember) {
    MemberId node = path.target();
    while (!node.equals(path.busiest())) {
      primaryByPartition.put(path.cameFromPartition().get(node), node);
      node = path.cameFromMember().get(node);
    }
    leaderCountByMember.merge(path.busiest(), -1, Integer::sum);
    leaderCountByMember.merge(path.target(), 1, Integer::sum);
  }

  /**
   * @throws IllegalArgumentException if {@code targetMembers} is empty, {@code replicationFactor}
   *     is not positive, or {@code targetMembers} has fewer members than {@code replicationFactor}
   *     — that combination can never be satisfied and would otherwise silently under-replicate
   *     every partition instead of failing loudly
   */
  static void validateTargetMembers(
      final Set<MemberId> targetMembers, final int replicationFactor) {
    if (targetMembers.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected at least one target member, but targetMembers is empty");
    }
    if (replicationFactor < 1) {
      throw new IllegalArgumentException(
          "Expected replicationFactor to be at least 1, but was " + replicationFactor);
    }
    if (targetMembers.size() < replicationFactor) {
      throw new IllegalArgumentException(
          "Expected at least %d target member(s) to satisfy replicationFactor, but targetMembers has only %d"
              .formatted(replicationFactor, targetMembers.size()));
    }
  }

  /**
   * Validates that every partition id that currently exists — in every group, not just ones
   * mentioned in {@code targetPartitionIds} — is present in {@code targetPartitionIds}. These
   * implementations do not support removing a partition or an entire group: {@code
   * targetPartitionIds} must describe the complete desired state of the whole cluster's groups, not
   * just the ones being changed. A group with no ids at all in {@code targetPartitionIds} is only
   * permitted if it currently has no partitions of its own (e.g. a brand-new, empty group
   * pre-seeded before this call) — there is nothing to silently remove in that case.
   *
   * @param distributionByGroup every group's current distribution, e.g. from {@link
   *     ConfigurationUtil#getPartitionDistributionPerPhysicalTenant}
   * @throws IllegalArgumentException if any existing partition id, in any group, is missing from
   *     {@code targetPartitionIds}
   */
  static void validateExistingPartitionsAreNotRemoved(
      final Map<String, Set<PartitionMetadata>> distributionByGroup,
      final List<PartitionId> targetPartitionIds) {
    final Set<PartitionId> targetIdSet = Set.copyOf(targetPartitionIds);
    final var missing =
        distributionByGroup.values().stream()
            .flatMap(Set::stream)
            .map(PartitionMetadata::id)
            .filter(id -> !targetIdSet.contains(id))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalArgumentException(
          "targetPartitionIds must include every existing partition id of every existing group — "
              + "removing partitions or groups is not supported by this reassigner, but is "
              + "missing: "
              + missing);
    }
  }

  /**
   * An existing partition changing shape: join new members, have removed members leave, and
   * reconfigure the priority of any member present in both whose priority changed. A target
   * identical to the current state produces no operations at all.
   */
  public static List<PartitionGroupOperation> movePartition(
      final PartitionMetadata current, final PartitionMetadata target) {
    final int partitionId = target.id().number();
    final List<PartitionGroupOperation> operations = new ArrayList<>();

    final var membersToJoin =
        target.members().stream()
            .filter(member -> !current.members().contains(member))
            .map(
                newMember ->
                    (PartitionGroupOperation)
                        new PartitionJoinOperation(
                            newMember, partitionId, target.getPriority(newMember)))
            .sorted(Comparator.comparing(PartitionGroupOperation::memberId))
            .toList();
    final var membersToLeave =
        current.members().stream()
            .filter(member -> !target.members().contains(member))
            .map(
                oldMember ->
                    (PartitionGroupOperation)
                        new PartitionLeaveOperation(oldMember, partitionId, 1))
            .sorted(Comparator.comparing(PartitionGroupOperation::memberId))
            .toList();
    final var membersToChangePriority =
        current.members().stream()
            .filter(member -> target.members().contains(member))
            .filter(member -> target.getPriority(member) != current.getPriority(member))
            .map(
                member ->
                    (PartitionGroupOperation)
                        new PartitionReconfigurePriorityOperation(
                            member, partitionId, target.getPriority(member)))
            .sorted(Comparator.comparing(PartitionGroupOperation::memberId))
            .toList();

    operations.addAll(membersToJoin);
    operations.addAll(membersToLeave);
    operations.addAll(membersToChangePriority);
    return operations;
  }

  /**
   * The members and primary chosen for a newly placed partition by {@link
   * #selectNewPartitionMembers}.
   */
  record NewPartitionPlacement(List<MemberId> members, MemberId primary) {}

  /**
   * A chain of primary reassignments found by {@link #findAugmentingPath} that moves one unit of
   * leadership from {@code busiest} to {@code target}.
   *
   * @param cameFromMember for each member on the path (except {@code busiest}), the member it was
   *     reached from
   * @param cameFromPartition for each member on the path (except {@code busiest}), the eligible
   *     partition whose primary must change to reach it
   */
  private record AugmentingPath(
      MemberId busiest,
      MemberId target,
      Map<MemberId, MemberId> cameFromMember,
      Map<MemberId, PartitionId> cameFromPartition) {}
}
