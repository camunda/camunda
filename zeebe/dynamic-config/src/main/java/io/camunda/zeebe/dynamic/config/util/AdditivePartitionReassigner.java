/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.accumulateLoad;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.rebalanceLeaders;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.selectNewPartitionMembers;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.validateNoRemoval;
import static io.camunda.zeebe.dynamic.config.util.PartitionReassignmentSupport.validateTargetMembers;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link PartitionReassigner} for the case where only new partitions are being added — {@code
 * targetMembers} and {@code replicationFactor} are unchanged from every affected group's current
 * state. {@code targetPartitionIds} may span several groups in one call; every id already present
 * in its group's current distribution is passed through unchanged, and only ids that don't exist
 * yet are placed, greedily on the currently least-loaded members (breaking ties by leader count,
 * then {@link MemberId#ID_COMPARATOR} for determinism), updating its in-memory load view after
 * every placement so a batch of new partitions — even across different groups — is itself spread
 * evenly. New-partition placement processes ids in their natural {@link PartitionId} order
 * regardless of the order {@code targetPartitionIds} was given in, so the result only depends on
 * the id set, not on incidental list ordering.
 *
 * <p>This implementation never moves or otherwise touches an existing partition and never adjusts
 * an existing partition's replication factor — a future general-case reassigner is expected to
 * handle {@code targetMembers} or {@code replicationFactor} differing from a group's current state.
 *
 * <p><b>Known trade-off:</b> because existing partitions are never touched, leader (primary) count
 * across members is <i>not</i> always guaranteed to converge to the same tight balance (gap of 1)
 * that replica count is. If the existing distribution already has a leader-count skew, and few
 * enough new partitions are being added, there can be no valid new-partition placement that both
 * keeps replica count balanced and fully corrects that skew — moving leadership onto the
 * under-represented member would require also making it a replica of a new partition, which can
 * unbalance replica count instead. New-partition placement still actively works to reduce leader
 * imbalance wherever a fix is possible without sacrificing replica balance; the residual imbalance
 * is bounded by the replication factor.
 *
 * <p>Removing a partition or an entire group is not supported: {@code targetPartitionIds} must
 * include every existing partition id of every existing group, not just the ones being changed —
 * see {@link PartitionReassignmentSupport#validateNoRemoval}.
 */
public final class AdditivePartitionReassigner implements PartitionReassigner {

  @Override
  public Set<PartitionMetadata> reassignPartitions(
      final CurrentClusterConfiguration currentConfiguration,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds,
      final int replicationFactor) {

    validateTargetMembers(targetMembers, replicationFactor);
    final Set<String> targetGroups =
        targetPartitionIds.stream().map(PartitionId::group).collect(Collectors.toSet());

    final Map<String, Set<PartitionMetadata>> distributionByGroup =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(currentConfiguration);
    validateNoRemoval(distributionByGroup, targetPartitionIds, replicationFactor);
    final Map<PartitionId, PartitionMetadata> currentById =
        targetGroups.stream()
            .flatMap(group -> distributionByGroup.getOrDefault(group, Set.of()).stream())
            .collect(Collectors.toMap(PartitionMetadata::id, Function.identity()));

    final List<MemberId> sortedMembers =
        targetMembers.stream().sorted(MemberId.ID_COMPARATOR).toList();
    final int clusterSize = sortedMembers.size();
    final int replicaSlotCount = Math.min(replicationFactor, clusterSize);

    final Map<MemberId, Integer> replicaCountByMember = new HashMap<>();
    final Map<MemberId, Integer> leaderCountByMember = new HashMap<>();
    distributionByGroup
        .values()
        .forEach(
            partitions -> accumulateLoad(partitions, replicaCountByMember, leaderCountByMember));

    final List<PartitionId> processingOrder =
        targetPartitionIds.stream().distinct().sorted().toList();

    final List<PartitionId> newPartitionIds = new ArrayList<>();
    final Map<PartitionId, Set<MemberId>> newMembersById = new LinkedHashMap<>();
    final Map<PartitionId, MemberId> newPrimaryById = new LinkedHashMap<>();
    for (final PartitionId id : processingOrder) {
      if (currentById.containsKey(id)) {
        continue;
      }
      final var placement =
          selectNewPartitionMembers(
              sortedMembers, replicaSlotCount, replicaCountByMember, leaderCountByMember);
      newPartitionIds.add(id);
      newMembersById.put(id, new LinkedHashSet<>(placement.members()));
      newPrimaryById.put(id, placement.primary());
      placement.members().forEach(member -> replicaCountByMember.merge(member, 1, Integer::sum));
      leaderCountByMember.merge(placement.primary(), 1, Integer::sum);
    }

    // Fixes up leader-count balance across the new partitions by reassigning primary among their
    // already-selected replicas — never moving a replica, never touching an existing partition.
    rebalanceLeaders(
        newPartitionIds, newMembersById, newPrimaryById, sortedMembers, leaderCountByMember);

    final Set<PartitionMetadata> result = new HashSet<>();
    for (final PartitionId id : processingOrder) {
      final var existing = currentById.get(id);
      if (existing != null) {
        result.add(existing);
        continue;
      }

      final var membersForPartition = List.copyOf(newMembersById.get(id));
      final var primary = newPrimaryById.get(id);
      final var priorities =
          PartitionPriorityAssigner.assignPriorities(
              id, membersForPartition, primary, clusterSize, replicationFactor);

      result.add(
          new PartitionMetadata(
              id, Set.copyOf(membersForPartition), priorities, priorities.get(primary), primary));
    }
    return result;
  }
}
