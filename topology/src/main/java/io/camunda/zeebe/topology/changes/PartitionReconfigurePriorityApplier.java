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
import java.util.function.UnaryOperator;

public class PartitionReconfigurePriorityApplier implements OperationApplier {

  private final int partitionId;
  private final int newPriority;
  private final MemberId localMemberId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public PartitionReconfigurePriorityApplier(
      final int partitionId,
      final int priority,
      final MemberId memberId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionId = partitionId;
    newPriority = priority;
    localMemberId = memberId;
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
              "Expected to change priority of a partition, but the local member is not active"));
    }

    final MemberState localMemberState = currentClusterTopology.getMember(localMemberId);
    final boolean partitionExistsInLocalMember = localMemberState.hasPartition(partitionId);
    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to change priority of partition %d, but the local member does not have the partition",
                  partitionId)));
    }
    final PartitionState.State partitionState = localMemberState.getPartition(partitionId).state();
    if (partitionState != PartitionState.State.ACTIVE) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to change priority of partition %d, but the local member has partition in state %s",
                  partitionId, partitionState)));
    }

    // Nothing to change in the state
    return Either.right(memberState -> memberState);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> apply() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .reconfigurePriority(partitionId, newPriority)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    memberState ->
                        memberState.updatePartition(
                            partitionId,
                            partitionState ->
                                new PartitionState(partitionState.state(), newPriority)));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }
}
