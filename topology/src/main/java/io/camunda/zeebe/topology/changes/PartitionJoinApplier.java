/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.OperationApplier;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.MemberState.State;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A Partition Join operation is executed when a member wants to start replicating a partition. This
 * is allowed only when the member is active, and the partition is not already active.
 */
final class PartitionJoinApplier implements OperationApplier {
  private final int partitionId;
  private final int priority;
  private final PartitionChangeExecutor partitionChangeExecutor;
  private final MemberId localMemberId;
  private Map<MemberId, Integer> partitionMembersWithPriority;

  PartitionJoinApplier(
      final int partitionId,
      final int priority,
      final MemberId localMemberId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    this.priority = priority;
    this.localMemberId = localMemberId;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> init(
      final ClusterTopology currentClusterTopology) {

    final boolean localMemberIsActive =
        currentClusterTopology.hasMember(localMemberId)
            && currentClusterTopology.getMember(localMemberId).state() == State.ACTIVE;
    if (!localMemberIsActive) {
      return Either.left(
          new IllegalStateException(
              "Expected to join partition, but the local member is not active"));
    }

    final MemberState localMemberState = currentClusterTopology.getMember(localMemberId);
    final boolean partitionExistsInLocalMember = localMemberState.hasPartition(partitionId);
    if (partitionExistsInLocalMember
        && localMemberState.getPartition(partitionId).state() != PartitionState.State.JOINING) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to join partition %s, but the local member already has the partition at state %s",
                  partitionId, localMemberState.partitions().get(partitionId).state())));
    }

    // Collect the priority of each member, including the local member. This is needed to generate
    // PartitionMetadata when joining the partition.
    partitionMembersWithPriority = collectPriorityByMembers(currentClusterTopology);

    if (partitionExistsInLocalMember
        && localMemberState.getPartition(partitionId).state() == PartitionState.State.JOINING) {
      // The state is already JOINING, so we don't need to change it. This can happen when the node
      // was restarted while applying the join operation. To ensure that the topology change can
      // make progress, we do not treat this as an error.
      return Either.right(memberState -> memberState);
    } else {
      return Either.right(
          memberState -> memberState.addPartition(partitionId, PartitionState.joining(priority)));
    }
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> apply() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .join(partitionId, partitionMembersWithPriority)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    memberState ->
                        memberState.updatePartition(partitionId, PartitionState::toActive));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }

  private HashMap<MemberId, Integer> collectPriorityByMembers(
      final ClusterTopology currentClusterTopology) {
    final var priorityMap = new HashMap<MemberId, Integer>();
    currentClusterTopology
        .members()
        .forEach(
            (memberId, memberState) -> {
              if (memberState.partitions().containsKey(partitionId)) {
                final var partitionState = memberState.partitions().get(partitionId);
                priorityMap.put(memberId, partitionState.priority());
              }
            });

    priorityMap.put(localMemberId, priority);
    return priorityMap;
  }
}
