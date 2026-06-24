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
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class ExitRecoveryApplier implements MemberOperationApplier {
  private static final String TRANSITION_ERROR_MESSAGE =
      "Expected to exit recovery for member %s, but the member is not part of the cluster";
  private final MemberId memberId;
  private final ModeChangeExecutor recoveryModeChangeExecutor;

  public ExitRecoveryApplier(
      final MemberId memberId, final ModeChangeExecutor recoveryModeChangeExecutor) {
    this.memberId = memberId;
    this.recoveryModeChangeExecutor = recoveryModeChangeExecutor;
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
          new IllegalStateException(String.format(TRANSITION_ERROR_MESSAGE, memberId)));
    }

    final var memberState = currentClusterConfiguration.getMember(memberId).state();

    if (State.ACTIVE.equals(memberState)) {
      return Either.right(UnaryOperator.identity());
    }

    if (!State.RECOVERING.equals(memberState)) {
      return Either.left(
          new IllegalStateException(String.format(TRANSITION_ERROR_MESSAGE, memberId)));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
    final CompletableActorFuture<UnaryOperator<MemberState>> result =
        new CompletableActorFuture<>();
    recoveryModeChangeExecutor
        .exitRecovery()
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(MemberState::toActive);
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }
}
