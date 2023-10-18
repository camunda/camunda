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
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.topology.PartitionDistributor;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.AddMembersRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.topology.api.TopologyManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.StatusCode;
import io.camunda.zeebe.topology.api.TopologyManagementResponse.TopologyChangeStatus;
import io.camunda.zeebe.topology.changes.TopologyChangeCoordinator;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
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

/**
 * Handles the requests for the topology management. This is expected be running on the coordinator
 * node.
 */
final class TopologyManagementRequestsHandler implements TopologyManagementApi {

  private final TopologyChangeCoordinator coordinator;
  private final ConcurrencyControl executor;

  public TopologyManagementRequestsHandler(
      final TopologyChangeCoordinator coordinator, final ConcurrencyControl executor) {
    this.coordinator = coordinator;
    this.executor = executor;
  }

  @Override
  public ActorFuture<TopologyChangeStatus> addMembers(final AddMembersRequest addMembersRequest) {
    return applyOperations(
        addMembersRequest.members().stream()
            .map(MemberJoinOperation::new)
            .map(TopologyChangeOperation.class::cast)
            .toList());
  }

  @Override
  public ActorFuture<TopologyChangeStatus> joinPartition(
      final JoinPartitionRequest joinPartitionRequest) {
    return applyOperations(
        List.of(
            new PartitionJoinOperation(
                joinPartitionRequest.memberId(),
                joinPartitionRequest.partitionId(),
                joinPartitionRequest.priority())));
  }

  @Override
  public ActorFuture<TopologyChangeStatus> leavePartition(
      final LeavePartitionRequest leavePartitionRequest) {
    return applyOperations(
        List.of(
            new PartitionLeaveOperation(
                leavePartitionRequest.memberId(), leavePartitionRequest.partitionId())));
  }

  @Override
  public ActorFuture<TopologyChangeStatus> reassignPartitions(
      final ReassignPartitionsRequest reassignPartitionsRequest) {
    final ActorFuture<TopologyChangeStatus> responseFuture = executor.createFuture();

    if (reassignPartitionsRequest.members() == null
        || reassignPartitionsRequest.members().isEmpty()) {
      responseFuture.completeExceptionally(
          new IllegalArgumentException("Cannot reassign partitions if no brokers are provided"));
      return responseFuture;
    }

    executor.runOnCompletion(
        coordinator.getCurrentTopology(),
        (clusterTopology, error) -> {
          if (error == null) {
            final var generatedOperation =
                generatePartitionDistributionOperations(
                    clusterTopology, reassignPartitionsRequest.members());
            if (generatedOperation.isLeft()) {
              responseFuture.completeExceptionally(generatedOperation.getLeft());
            } else {
              final var operations = generatedOperation.get();
              if (!operations.isEmpty()) {
                applyOperations(operations).onComplete(responseFuture);
              } else {
                responseFuture.complete(
                    new TopologyChangeStatus(clusterTopology.version(), StatusCode.COMPLETED));
              }
            }
          } else {
            responseFuture.completeExceptionally(error);
          }
        });
    return responseFuture;
  }

  private ActorFuture<TopologyChangeStatus> applyOperations(
      final List<TopologyChangeOperation> operations) {
    final ActorFuture<TopologyChangeStatus> responseFuture = executor.createFuture();
    coordinator
        .applyOperations(operations)
        .onComplete(
            (topology, error) -> {
              if (error == null) {
                final var status =
                    new TopologyChangeStatus(topology.version(), StatusCode.IN_PROGRESS);
                responseFuture.complete(status);
              } else {
                responseFuture.completeExceptionally(error);
              }
            });

    return responseFuture;
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
          new IllegalArgumentException(
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
