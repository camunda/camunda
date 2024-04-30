/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.topology.PartitionDistributor;
import io.camunda.zeebe.topology.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.changes.ConfigurationChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterConfiguration;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.topology.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.topology.util.TopologyUtil;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** Reassign all partitions to the given members based on round-robin strategy. */
public class PartitionReassignRequestTransformer implements TopologyChangeRequest {
  final Set<MemberId> members;
  private final Optional<Integer> newReplicationFactor;

  public PartitionReassignRequestTransformer(
      final Set<MemberId> members, final Optional<Integer> newReplicationFactor) {
    this.members = members;
    this.newReplicationFactor = newReplicationFactor;
  }

  public PartitionReassignRequestTransformer(final Set<MemberId> members) {
    this(members, Optional.empty());
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration currentTopology) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }

    return generatePartitionDistributionOperations(currentTopology, members);
  }

  private int getReplicationFactor(final ClusterConfiguration clusterConfiguration) {
    return newReplicationFactor.orElse(clusterConfiguration.minReplicationFactor());
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>>
      generatePartitionDistributionOperations(
          final ClusterConfiguration currentTopology, final Set<MemberId> brokers) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var oldDistribution = TopologyUtil.getPartitionDistributionFrom(currentTopology, "temp");
    final int replicationFactor = getReplicationFactor(currentTopology);

    if (replicationFactor <= 0) {
      return Either.left(
          new ClusterConfigurationRequestFailedException.InvalidRequest(
              String.format("Replication factor [%d] must be greater than 0", replicationFactor)));
    }

    if (brokers.size() < replicationFactor) {
      return Either.left(
          new ClusterConfigurationRequestFailedException.InvalidRequest(
              String.format(
                  "Number of brokers [%d] is less than the replication factor [%d]",
                  brokers.size(), replicationFactor)));
    }

    final var partitionCount = currentTopology.partitionCount();
    final var sortedPartitions =
        IntStream.rangeClosed(1, partitionCount)
            .mapToObj(i -> PartitionId.from("temp", i))
            .sorted()
            .toList();

    final PartitionDistributor roundRobinDistributor = new RoundRobinPartitionDistributor();

    final var newDistribution =
        roundRobinDistributor.distributePartitions(brokers, sortedPartitions, replicationFactor);

    for (final PartitionMetadata newMetadata : newDistribution) {
      final var oldMetadata =
          oldDistribution.stream()
              .filter(old -> old.id().id().equals(newMetadata.id().id()))
              .findFirst()
              .orElseThrow();

      operations.addAll(movePartition(oldMetadata, newMetadata));
    }

    return Either.right(operations);
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
