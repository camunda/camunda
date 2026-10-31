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
 * Demotes a departing member of a partition to a non-voting member - the first phase of a two-phase
 * leave, so that the subsequent leave commits without the departing member's participation. The
 * partition is marked {@code LEAVING} here already; the leave applier's re-entry branch accepts
 * that and removes the partition once the member has left.
 */
record PartitionDemoteApplier(
    int partitionId, MemberId localMemberId, PartitionChangeExecutor partitionChangeExecutor)
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
              "Expected to demote member in partition %d, but the local member does not exist in the cluster"
                  .formatted(partitionId)));
    }

    final var localMember = currentClusterConfiguration.getMember(localMemberId);
    if (!localMember.hasPartition(partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to demote member in partition %d, but the local member does not have the partition"
                  .formatted(partitionId)));
    }

    if (localMember.getPartition(partitionId).state() == PartitionState.State.LEAVING) {
      // The node restarted while applying the demote operation. The retried demotion is a no-op at
      // the raft layer, so we do not treat this as an error.
      return Either.right(UnaryOperator.identity());
    }

    // A non-empty replication group without any voting member could neither elect a leader nor
    // commit, and the leader rejects such a configuration - the demotion could never succeed and
    // the change would be stuck. Transformers only emit a demotion when another active member
    // remains; this guards against plans that violate that.
    final var otherActiveReplicaExists =
        currentClusterConfiguration.members().entrySet().stream()
            .filter(entry -> !entry.getKey().equals(localMemberId))
            .filter(entry -> entry.getValue().hasPartition(partitionId))
            .anyMatch(
                entry -> entry.getValue().getPartition(partitionId).state().isActiveReplica());
    if (!otherActiveReplicaExists) {
      return Either.left(
          new IllegalStateException(
              "Expected to demote member in partition %d, but no other member has the partition in active state"
                  .formatted(partitionId)));
    }

    return Either.right(
        memberState -> memberState.updatePartition(partitionId, PartitionState::toLeaving));
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .demote(partitionId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(UnaryOperator.identity());
              } else {
                result.completeExceptionally(error);
              }
            });

    return result;
  }
}
