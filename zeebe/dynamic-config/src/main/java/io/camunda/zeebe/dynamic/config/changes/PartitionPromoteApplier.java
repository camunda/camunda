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
 * Promotes a member that joined a partition as a non-voting learner to a full voting member - the
 * second phase of a two-phase join. The raft leader rejects the promotion until the member has
 * caught up; that surfaces as a failed {@link #applyOperation()}, which the configuration manager
 * retries with backoff, so the retry loop is the catch-up poll. Init deliberately carries no
 * transient condition, since init failures are not retried on a timer.
 */
record PartitionPromoteApplier(
    int partitionId, MemberId localMemberId, PartitionChangeExecutor partitionChangeExecutor)
    implements MemberOperationApplier {

  @Override
  public MemberId memberId() {
    return localMemberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {

    final boolean localMemberIsActive =
        currentClusterConfiguration.hasMember(localMemberId)
            && currentClusterConfiguration.getMember(localMemberId).state()
                == MemberState.State.ACTIVE;
    if (!localMemberIsActive) {
      return Either.left(
          new IllegalStateException(
              "Expected to promote member in partition %d, but the local member is not an active member of the cluster"
                  .formatted(partitionId)));
    }

    final var localMember = currentClusterConfiguration.getMember(localMemberId);
    if (!localMember.hasPartition(partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to promote member in partition %d, but the local member does not have the partition"
                  .formatted(partitionId)));
    }

    return switch (localMember.getPartition(partitionId).state()) {
      // ACTIVE can happen if the node restarted after the promotion completed but before the
      // operation was recorded. The retried promotion is a no-op at the raft layer, so we do not
      // treat this as an error.
      case LEARNER, ACTIVE -> Either.right(UnaryOperator.identity());
      default ->
          Either.left(
              new IllegalStateException(
                  "Expected to promote member in partition %d, but the partition is in state %s"
                      .formatted(partitionId, localMember.getPartition(partitionId).state())));
    };
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();

    partitionChangeExecutor
        .promote(partitionId)
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
}
