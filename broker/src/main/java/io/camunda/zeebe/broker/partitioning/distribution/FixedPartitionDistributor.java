/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.PartitionDistributor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A {@link PartitionDistributor} implementation which takes in a provided, fixed mapping which
 * already describes which member maps to which partitions, and returns the appropriate set of
 * distributed partitions on demand.
 *
 * <p>See {@link FixedPartitionDistributorBuilder} to build a new instance. The class is
 * intentionally not publicly instantiable to reduce the risk of configuration errors.
 */
public final class FixedPartitionDistributor implements PartitionDistributor {
  private final Map<PartitionId, Set<FixedDistributionMember>> distribution;

  FixedPartitionDistributor(final Map<PartitionId, Set<FixedDistributionMember>> distribution) {
    this.distribution = distribution;
  }

  /**
   * Generates a partition distribution based on the initial configuration, using the input here
   * mostly for validation.
   *
   * @param clusterMembers the set of members that can own partitions
   * @param sortedPartitionIds a sorted list of partition IDs
   * @param replicationFactor the replication factor for each partition
   * @return a set of distributed partitions, containing the set of members to which they belong to
   * @throws IllegalStateException if any of the configured members are not found in the set of
   *     {@code clusterMembers}; this implies that these members do not exist in the cluster
   * @throws IllegalStateException if at least one of the partitions in {@code sortedPartitionIds}
   *     is not distributed over any members
   * @throws IllegalStateException if at least one of the partitions in {@code sortedPartitionIds}
   *     does not have exactly {@code replicationFactor} members
   */
  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {
    final var partitions = new HashSet<PartitionMetadata>();
    for (final var partitionId : sortedPartitionIds) {
      final var metadata = createPartitionMetadata(clusterMembers, replicationFactor, partitionId);
      partitions.add(metadata);
    }

    return partitions;
  }

  private PartitionMetadata createPartitionMetadata(
      final Set<MemberId> clusterMembers,
      final int replicationFactor,
      final PartitionId partitionId) {
    final var members = new ArrayList<MemberId>();
    final var priorities = new HashMap<MemberId, Integer>();
    final var configuredMembers = distribution.get(partitionId);
    int targetPriority = 0;

    if (configuredMembers == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to distribute partition %d, but no members configured for it",
              partitionId.id()));
    }

    for (final var member : configuredMembers) {
      members.add(member.getId());
      priorities.put(member.getId(), member.getPriority());
      targetPriority = Math.max(targetPriority, member.getPriority());
    }

    ensureMembersArePartOfCluster(clusterMembers, partitionId, members);
    ensurePartitionIsFullyReplicated(replicationFactor, partitionId, members);

    return new PartitionMetadata(partitionId, members, priorities, targetPriority);
  }

  private void ensureMembersArePartOfCluster(
      final Set<MemberId> clusterMembers,
      final PartitionId partitionId,
      final ArrayList<MemberId> members) {
    if (!clusterMembers.containsAll(members)) {
      final var unknownMembers = new HashSet<>(members);
      unknownMembers.removeAll(clusterMembers);

      throw new IllegalStateException(
          String.format(
              "Expected partition %d to be replicated across a cluster made of members %s, but the "
                  + "following configured members %s are not part of the cluster",
              partitionId.id(), clusterMembers, unknownMembers));
    }
  }

  private void ensurePartitionIsFullyReplicated(
      final int replicationFactor,
      final PartitionId partitionId,
      final ArrayList<MemberId> members) {
    if (members.size() != replicationFactor) {
      throw new IllegalStateException(
          String.format(
              "Expected each partition to be replicated across exactly %d members, but partition %d"
                  + " is replicated across members %s",
              replicationFactor, partitionId.id(), members));
    }
  }
}
