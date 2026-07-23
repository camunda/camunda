/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/** Shared validation and completion logic for the legacy-model restore step appliers. */
final class RestoreAppliers {

  private RestoreAppliers() {}

  static Either<Exception, UnaryOperator<MemberState>> requireRecoveringMember(
      final ClusterConfiguration clusterConfiguration,
      final MemberId memberId,
      final int partitionId) {
    if (!clusterConfiguration.hasMember(memberId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for member %s, but the member is not part of the cluster"
                  .formatted(memberId)));
    }
    final var member = clusterConfiguration.getMember(memberId);
    if (member.state() != State.RECOVERING) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for member %s, but the member is not in recovery (state %s)"
                  .formatted(memberId, member.state())));
    }
    if (!member.hasPartition(partitionId)) {
      return Either.left(
          new IllegalStateException(
              "Expected to apply restore operation for partition %d on member %s, but the member does not replicate that partition"
                  .formatted(partitionId, memberId)));
    }
    return Either.right(UnaryOperator.identity());
  }

  static ActorFuture<UnaryOperator<MemberState>> applyIdentity(final ActorFuture<Void> step) {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();
    step.onComplete(
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
