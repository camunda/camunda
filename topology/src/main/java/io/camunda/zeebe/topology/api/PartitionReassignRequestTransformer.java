/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.topology.PartitionDistributor;
import io.camunda.zeebe.topology.api.TopologyRequestFailedException.InvalidRequest;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator.TopologyChangeRequest;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.topology.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.topology.util.TopologyUtil;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/** Reassign all partitions to the given members based on round-robin strategy. */
public class PartitionReassignRequestTransformer implements TopologyChangeRequest {

  final Set<MemberId> members;

  public PartitionReassignRequestTransformer(final Set<MemberId> members) {
    this.members = members;
  }

  @Override
  public Either<Exception, List<TopologyChangeOperation>> operations(
      final ClusterTopology currentTopology) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }

    return generatePartitionDistributionOperations(currentTopology, members);
  }

  private Either<Exception, List<TopologyChangeOperation>> generatePartitionDistributionOperations(
      final ClusterTopology currentTopology, final Set<MemberId> brokers) {
    final List<TopologyChangeOperation> operations = new ArrayList<>();

    final var oldDistribution = TopologyUtil.getPartitionDistributionFrom(currentTopology, "temp");
    // We assume that all partitions have the same replication factor
    final int replicationFactor =
        oldDistribution.stream().map(p -> p.members().size()).findFirst().orElseThrow();

    if (brokers.size() < replicationFactor) {
      return Either.left(
          new TopologyRequestFailedException.InvalidRequest(
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

  private List<TopologyChangeOperation> movePartition(
      final PartitionMetadata oldMetadata, final PartitionMetadata newMetadata) {
    final Integer partitionId = newMetadata.id().id();
    final List<TopologyChangeOperation> operations = new ArrayList<>();

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
