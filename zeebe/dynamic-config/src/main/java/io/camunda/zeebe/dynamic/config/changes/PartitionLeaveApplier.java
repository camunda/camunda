/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * A partition leave operation is executed when a member wants to stop replicating a partition. This
 * is allowed only when the member is already replicating the partition.
 */
record PartitionLeaveApplier(
    int partitionId,
    MemberId localMemberId,
    int minimumAllowedReplicas,
    PartitionChangeExecutor partitionChangeExecutor)
    implements MemberOperationApplier {

  @Override
  public MemberId memberId() {
    return localMemberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {

    if (!currentClusterConfiguration.hasMember(localMemberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to leave partition, but the local member does not exist in the cluster"));
    }

    final boolean partitionExistsInLocalMember =
        currentClusterConfiguration.getMember(localMemberId).hasPartition(partitionId);

    if (!partitionExistsInLocalMember) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to leave partition, but the local member does not have the partition %s",
                  partitionId)));
    }

    final boolean partitionIsLeaving =
        currentClusterConfiguration.getMember(localMemberId).getPartition(partitionId).state()
            == PartitionState.State.LEAVING;

    if (partitionIsLeaving) {
      // If partition state is already set to leaving, then we don't need to set it again. This can
      // happen if the node was restarted while applying the leave operation. To ensure that the
      // configuration change can make progress, we do not treat this as an error.
      return Either.right(m -> m);
    } else {
      // The number of active replicas that would remain once this member leaves - counting only
      // members that durably participate in the quorum right now (see
      // PartitionState.State#isActiveReplica): a learner catching up, or another member already
      // on its way out, provides no redundancy and must not be counted toward the floor. Excluding
      // the local member by identity, rather than subtracting one from a count that includes it,
      // keeps this correct even when the leaving member is itself not an active replica (e.g. a
      // stuck learner being removed directly) - removing a non-voting member never changes how
      // many voting replicas remain, so that is always safe regardless of the minimum.
      final var remainingActiveReplicas =
          currentClusterConfiguration.members().entrySet().stream()
              .filter(entry -> !entry.getKey().equals(localMemberId))
              .filter(entry -> entry.getValue().hasPartition(partitionId))
              .filter(entry -> entry.getValue().getPartition(partitionId).state().isActiveReplica())
              .count();
      if (remainingActiveReplicas < minimumAllowedReplicas) {
        return Either.left(
            new IllegalStateException(
                String.format(
                    "Expected to leave partition, but the partition %s would have %d active "
                        + "replicas left but minimum allowed replicas is %d",
                    partitionId, remainingActiveReplicas, minimumAllowedReplicas)));
      }
      return Either.right(
          memberState -> memberState.updatePartition(partitionId, PartitionState::toLeaving));
    }
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
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
