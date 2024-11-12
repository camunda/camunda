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
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class MemberLeaveApplier implements MemberOperationApplier {

  private final MemberId memberId;
  private final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor;

  public MemberLeaveApplier(
      final MemberId memberId,
      final ClusterMembershipChangeExecutor clusterMembershipChangeExecutor) {
    this.memberId = memberId;
    this.clusterMembershipChangeExecutor = clusterMembershipChangeExecutor;
  }

  @Override
  public MemberId memberId() {
    return memberId;
  }

  @Override
  public Either<Exception, UnaryOperator<MemberState>> initMemberState(
      final ClusterConfiguration currentClusterConfiguration) {
    if (!currentClusterConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to remove member %s, but the member is not part of the cluster",
                  memberId)));
    }

    final boolean hasPartitions =
        !currentClusterConfiguration.getMember(memberId).partitions().isEmpty();
    if (hasPartitions) {
      return Either.left(
          new IllegalStateException(
              String.format(
                  "Expected to remove member %s, but the member still has partitions assigned. Partitions: [%s]",
                  memberId, currentClusterConfiguration.getMember(memberId).partitions())));
    }

    return Either.right(MemberState::toLeaving);
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final var future = new CompletableActorFuture<UnaryOperator<MemberState>>();
    clusterMembershipChangeExecutor
        .removeBroker(memberId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                future.complete(MemberState::toLeft);
              } else {
                future.completeExceptionally(error);
              }
            });

    return future;
  }
}
