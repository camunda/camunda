/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import static java.util.Objects.requireNonNull;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.ModeChangeOperation} targeting {@link
 * Mode#PROCESSING}, operating on a single named {@link PartitionGroupConfiguration}. Mirrors the
 * legacy {@code ExitRecoveryApplier} in {@code changes/}, which this does not replace or modify.
 * See {@link EnterRecoveryApplier} for the cluster-lifecycle vs. per-group-mode split rationale.
 */
public final class ExitRecoveryApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final ModeChangeExecutor modeChangeExecutor;

  public ExitRecoveryApplier(final MemberId memberId, final ModeChangeExecutor modeChangeExecutor) {
    this.memberId = memberId;
    this.modeChangeExecutor = modeChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final boolean localMemberIsActiveInCluster =
        currentGlobalConfiguration.hasMember(memberId)
            && requireNonNull(currentGlobalConfiguration.getMember(memberId)).state()
                == BrokerState.State.ACTIVE;
    if (!localMemberIsActiveInCluster) {
      return Either.left(
          new IllegalStateException(
              "Expected to exit recovery for member %s, but the member is not an active member of the cluster"
                  .formatted(memberId)));
    }

    final var localBroker = currentPartitionGroupConfiguration.getMember(memberId);
    if (localBroker == null) {
      return Either.left(
          new IllegalStateException(
              "Expected to exit recovery for member %s, but the member is not part of this partition group"
                  .formatted(memberId)));
    }

    // Already PROCESSING: this can happen if the node restarted while applying this operation.
    // To ensure that the configuration change can make progress, we do not treat this as an
    // error.
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>> result =
        new CompletableActorFuture<>();
    modeChangeExecutor
        .exitRecovery()
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                result.complete(
                    group ->
                        group.updateMember(memberId, broker -> broker.setMode(Mode.PROCESSING)));
              } else {
                result.completeExceptionally(error);
              }
            });
    return result;
  }
}
