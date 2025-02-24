/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link PartitionDistributor} implementation which takes in a provided, fixed mapping which
 * already describes which member maps to which partitions, and returns the appropriate set of
 * distributed partitions on demand.
 *
 * <p>See {@link FixedPartitionDistributorBuilder} to build a new instance. The class is
 * intentionally not publicly instantiable to reduce the risk of configuration errors.
 */
public final class FixedPartitionDistributor implements PartitionDistributor {
  private Map<PartitionId, Set<FixedDistributionMember>> distribution;

  FixedPartitionDistributor(final Map<PartitionId, Set<FixedDistributionMember>> distribution) {
    this.distribution = distribution;
  }

  public void setDistribution(final Map<PartitionId, Set<FixedDistributionMember>> distribution) {
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
    final var configuredMembers = distribution.get(partitionId);

    if (configuredMembers == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to distribute partition %d, but no members configured for it",
              partitionId.id()));
    }

    final var priorities =
        configuredMembers.stream()
            .collect(
                Collectors.toMap(
                    FixedDistributionMember::getId, FixedDistributionMember::getPriority));
    final int targetPriority = Collections.max(priorities.values());

    final var members = priorities.keySet();
    final var primaries =
        priorities.entrySet().stream()
            .filter(entry -> entry.getValue() == targetPriority)
            .map(Entry::getKey)
            .collect(Collectors.toList());

    MemberId primary = null;
    if (primaries.size() == 1) {
      primary = primaries.get(0);
    }

    ensureMembersArePartOfCluster(clusterMembers, partitionId, members);
    ensurePartitionIsFullyReplicated(replicationFactor, partitionId, members);

    return new PartitionMetadata(partitionId, members, priorities, targetPriority, primary);
  }

  private void ensureMembersArePartOfCluster(
      final Set<MemberId> clusterMembers,
      final PartitionId partitionId,
      final Set<MemberId> members) {
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
      final int replicationFactor, final PartitionId partitionId, final Set<MemberId> members) {
    if (members.size() != replicationFactor) {
      throw new IllegalStateException(
          String.format(
              "Expected each partition to be replicated across exactly %d members, but partition %d"
                  + " is replicated across members %s",
              replicationFactor, partitionId.id(), members));
    }
  }

  private int getReplicationFactor(final ClusterConfiguration clusterConfiguration) {
    return clusterConfiguration.minReplicationFactor();
  }

  private int getPartitionCount(final ClusterConfiguration clusterConfiguration) {
    return clusterConfiguration.partitionCount();
  }

  Either<Exception, List<ClusterConfigurationChangeOperation>>
      newGeneratePartitionDistributionOperations(
          final ClusterConfiguration currentConfiguration,
          final Set<MemberId> brokers,
          final Set<PartitionMetadata> newPartitionDistribution) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var oldDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(currentConfiguration, "temp");

    final int partitionCount = getPartitionCount(currentConfiguration);
    if (partitionCount < currentConfiguration.partitionCount()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "New partition count [%d] must be greater than or equal to the current partition count [%d]",
                  partitionCount, currentConfiguration.partitionCount())));
    }

    final int replicationFactor = getReplicationFactor(currentConfiguration);
    if (replicationFactor <= 0) {
      return Either.left(
          new InvalidRequest(
              String.format("Replication factor [%d] must be greater than 0", replicationFactor)));
    }

    if (brokers.size() < replicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Number of brokers [%d] is less than the replication factor [%d]",
                  brokers.size(), replicationFactor)));
    }

    // Can bootstrap partitions only in the sorted order
    final var sortedPartitionMetadata =
        newPartitionDistribution.stream()
            .sorted(Comparator.comparingInt(p -> p.id().id()))
            .toList();
    for (final PartitionMetadata newMetadata : sortedPartitionMetadata) {
      oldDistribution.stream()
          .filter(old -> old.id().id().equals(newMetadata.id().id()))
          .findFirst()
          .ifPresentOrElse(
              oldMetadata -> operations.addAll(movePartition(oldMetadata, newMetadata)),
              () -> operations.addAll(addPartition(newMetadata)));
    }

    return Either.right(operations);
  }

  private List<ClusterConfigurationChangeOperation> addPartition(
      final PartitionMetadata newMetadata) {
    final Integer partitionId = newMetadata.id().id();
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    // Bootstrap the partition in the primary
    final var primary =
        newMetadata.getPrimary().orElse(newMetadata.members().stream().findAny().orElseThrow());
    operations.add(
        new PartitionBootstrapOperation(primary, partitionId, newMetadata.getPriority(primary)));

    // Join each remaining members to the partition
    for (final MemberId member : newMetadata.members()) {
      if (!member.equals(primary)) {
        operations.add(
            new PartitionJoinOperation(member, partitionId, newMetadata.getPriority(member)));
      }
    }

    return operations;
  }

  private List<ClusterConfigurationChangeOperation> movePartition(
      final PartitionMetadata oldMetadata, final PartitionMetadata newMetadata) {
    final Integer partitionId = newMetadata.id().id();
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var membersToJoin =
        newMetadata.members().stream()
            .filter(member -> !oldMetadata.members().contains(member))
            .map(
                newMember ->
                    new PartitionJoinOperation(
                        newMember, partitionId, newMetadata.getPriority(newMember)))
            .toList();
    final var membersToLeave =
        oldMetadata.members().stream()
            .filter(member -> !newMetadata.members().contains(member))
            .map(oldMember -> new PartitionLeaveOperation(oldMember, partitionId))
            .toList();
    final var membersToChangePriority =
        oldMetadata.members().stream()
            .filter(memberId -> newMetadata.members().contains(memberId))
            .filter(
                memberId -> newMetadata.getPriority(memberId) != oldMetadata.getPriority(memberId))
            .map(
                memberId ->
                    new PartitionReconfigurePriorityOperation(
                        memberId, partitionId, newMetadata.getPriority(memberId)))
            .toList();

    // TODO: interleave join and leave operation
    operations.addAll(membersToJoin);
    operations.addAll(membersToLeave);
    operations.addAll(membersToChangePriority);
    return operations;
  }
}
