/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Distributes partitions across members using deterministic round-robin assignment.
 *
 * <p>Each partition is assigned to {@code replicationFactor} members. The first member in the
 * rotation is the preferred leader. Leadership preference rotates across members.
 *
 * <p>Example with 3 members, 3 partitions, replication factor 3:
 *
 * <pre>
 * Partition 1: [member-0, member-1, member-2]  → member-0 preferred leader
 * Partition 2: [member-1, member-2, member-0]  → member-1 preferred leader
 * Partition 3: [member-2, member-0, member-1]  → member-2 preferred leader
 * </pre>
 *
 * <p>Example with 3 members, 6 partitions, replication factor 2:
 *
 * <pre>
 * Partition 1: [member-0, member-1]
 * Partition 2: [member-1, member-2]
 * Partition 3: [member-2, member-0]
 * Partition 4: [member-0, member-1]
 * Partition 5: [member-1, member-2]
 * Partition 6: [member-2, member-0]
 * </pre>
 */
public final class RoundRobinPartitionDistributor implements PartitionDistributor {

  private final String groupName;

  public RoundRobinPartitionDistributor(final String groupName) {
    this.groupName = groupName;
  }

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final List<MemberId> clusterMembers, final int partitionCount, final int replicationFactor) {
    validate(clusterMembers, partitionCount, replicationFactor);

    final var partitions = new LinkedHashSet<PartitionMetadata>(partitionCount);

    for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
      final var members =
          membersForPartition(
              partitionId, clusterMembers, clusterMembers.size(), replicationFactor);
      partitions.add(buildMetadata(partitionId, members));
    }

    return partitions;
  }

  @Override
  public PartitionDistribution distributeForMember(
      final MemberId localMemberId,
      final List<MemberId> clusterMembers,
      final int partitionCount,
      final int replicationFactor) {
    validate(clusterMembers, partitionCount, replicationFactor);

    if (!clusterMembers.contains(localMemberId)) {
      throw new IllegalArgumentException(
          "Local member " + localMemberId + " is not part of the cluster " + clusterMembers);
    }

    final var result = new LinkedHashMap<Integer, Set<MemberId>>();
    for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
      final var members =
          membersForPartition(
              partitionId, clusterMembers, clusterMembers.size(), replicationFactor);

      if (members.contains(localMemberId)) {
        result.put(partitionId, members);
      }
    }

    return new PartitionDistribution(result);
  }

  private void validate(
      final List<MemberId> clusterMembers, final int partitionCount, final int replicationFactor) {

    if (clusterMembers.isEmpty()) {
      throw new IllegalArgumentException("Cluster members must not be empty");
    }

    if (partitionCount < 1) {
      throw new IllegalArgumentException(
          "Partition count must be at least 1, got " + partitionCount);
    }

    if (replicationFactor < 1) {
      throw new IllegalArgumentException(
          "Replication factor must be at least 1, got " + replicationFactor);
    }

    if (replicationFactor > clusterMembers.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Replication factor %d exceeds cluster size %d",
              replicationFactor, clusterMembers.size()));
    }
  }

  private static Set<MemberId> membersForPartition(
      final int partitionId,
      final List<MemberId> sortedMembers,
      final int memberCount,
      final int replicationFactor) {
    final var result = new LinkedHashSet<MemberId>(replicationFactor);
    for (int i = 0; i < replicationFactor; i++) {
      final var memberIndex = (partitionId - 1 + i) % memberCount;
      result.add(sortedMembers.get(memberIndex));
    }
    return result;
  }

  private PartitionMetadata buildMetadata(final int partitionId, final Set<MemberId> members) {
    final var raftPartitionId = PartitionId.from(groupName, partitionId);
    final Map<MemberId, Integer> priorities =
        members.stream().collect(Collectors.toMap(m -> m, m -> 1));
    final var preferredLeader = members.iterator().next();
    return new PartitionMetadata(raftPartitionId, members, priorities, 1, preferredLeader);
  }
}
