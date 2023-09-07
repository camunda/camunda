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
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.util.Either;
import java.util.Map;
import java.util.function.UnaryOperator;

record PartitionLeaveApplier(
    int partitionId,
    MemberId localMemberId,
    PartitionTopologyChangeExecutor partitionTopologyChangeExecutor)
    implements OperationApplier {

  @Override
  public Either<Exception, UnaryOperator<MemberState>> init(
      final ClusterTopology currentClusterTopology) {

    final Map<Integer, PartitionState> localPartitions =
        currentClusterTopology.members().get(localMemberId).partitions();
    final boolean partitionExistsInLocalMember = localPartitions.containsKey(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the local member does not have the partition"));
    }

    final boolean partitionIsLeaving =
        localPartitions.get(partitionId).state() == PartitionState.State.LEAVING;
    if (partitionIsLeaving) {
      // If partition state is already set to leaving, then we don't need to set it again
      return Either.right(m -> m);
    } else {
      return Either.right(
          memberState -> memberState.updatePartition(partitionId, PartitionState::toLeaving));
    }
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> apply() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionTopologyChangeExecutor
        .leave(partitionId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(memberState -> memberState.removePartition(partitionId));
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }
}
