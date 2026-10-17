/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.AwaitModeChangeOperation}, operating on a
 * single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * AwaitModeChangeApplier} in {@code changes/}, which this does not replace or modify. Waits for a
 * member's partition manager to finish starting in the target {@link Mode}. Writes {@link
 * PartitionState.State#RECOVERING} or {@link PartitionState.State#ACTIVE} for the subset of this
 * member's partitions that {@link ModeChangeExecutor#awaitModeApplied(Mode)} confirms transitioned;
 * partitions outside that subset (e.g. unhealthy ones) keep their prior state.
 */
public final class AwaitModeChangeApplier implements PartitionGroupConfigurationChangeApplier {

  private final MemberId memberId;
  private final Mode mode;
  private final ModeChangeExecutor modeChangeExecutor;

  public AwaitModeChangeApplier(
      final MemberId memberId, final Mode mode, final ModeChangeExecutor modeChangeExecutor) {
    this.memberId = Objects.requireNonNull(memberId);
    this.mode = Objects.requireNonNull(mode);
    this.modeChangeExecutor = Objects.requireNonNull(modeChangeExecutor);
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
    modeChangeExecutor
        .awaitModeApplied(mode)
        .onComplete(
            (confirmedPartitions, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(writeConfirmedPartitions(confirmedPartitions));
              }
            });
    return result;
  }

  private UnaryOperator<PartitionGroupConfiguration> writeConfirmedPartitions(
      final Set<Integer> confirmedPartitions) {
    if (confirmedPartitions.isEmpty()) {
      return UnaryOperator.identity();
    }
    final UnaryOperator<PartitionState> partitionStateUpdater =
        mode == Mode.RECOVERING ? PartitionState::toRecovering : PartitionState::toActive;
    return clusterConfiguration ->
        clusterConfiguration.updateMember(
            memberId,
            memberState -> {
              var updatedState = memberState;
              for (final var partitionId : confirmedPartitions) {
                updatedState = updatedState.updatePartition(partitionId, partitionStateUpdater);
              }
              return updatedState;
            });
  }
}
