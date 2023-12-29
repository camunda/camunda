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
import java.util.function.UnaryOperator;

/**
 * A partition leave operation is executed when a member wants to stop replicating a partition. This
 * is allowed only when the member is already replicating the partition.
 */
record PartitionLeaveApplier(
    int partitionId, MemberId localMemberId, PartitionChangeExecutor partitionChangeExecutor)
    implements OperationApplier {

  @Override
  public Either<Exception, UnaryOperator<MemberState>> init(
      final ClusterTopology currentClusterTopology) {

    if (!currentClusterTopology.hasMember(localMemberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the local member does not exist in the topology"));
    }

    final boolean partitionExistsInLocalMember =
        currentClusterTopology.getMember(localMemberId).hasPartition(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to leave partition, but the local member does not have the partition %s",
                  partitionId)));
    }

    final boolean partitionIsLeaving =
        currentClusterTopology.getMember(localMemberId).getPartition(partitionId).state()
            == PartitionState.State.LEAVING;
    if (partitionIsLeaving) {
      // If partition state is already set to leaving, then we don't need to set it again. This can
      // happen if the node was restarted while applying the leave operation. To ensure that the
      // topology change can make progress, we do not treat this as an error.
      return Either.right(m -> m);
    } else {
      final var partitionReplicaCount =
          currentClusterTopology.members().values().stream()
              .filter(m -> m.hasPartition(partitionId))
              .count();
      if (partitionReplicaCount <= 1) {
        return Either.left(
            new IllegalStateException(
                String.format(
                    "Expected to leave partition, but the partition %s has only one replica",
                    partitionId)));
      }
      return Either.right(
          memberState -> memberState.updatePartition(partitionId, PartitionState::toLeaving));
    }
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> apply() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
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
