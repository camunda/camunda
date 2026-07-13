/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.DeleteHistoryOperation}, operating on a
 * single named {@link PartitionGroupConfiguration} as a whole. Mirrors the legacy {@code
 * DeleteHistoryApplier} in {@code changes/}, which this does not replace or modify.
 *
 * <p>Ported against the current, unsplit {@link ClusterChangeExecutor} (same as the legacy applier)
 * — the split into a per-group history-deletion executor is Phase 3 scope, tracked separately.
 */
public final class DeleteHistoryApplier implements PartitionGroupConfigurationChangeApplier {

  private final ClusterChangeExecutor clusterChangeExecutor;

  public DeleteHistoryApplier(final ClusterChangeExecutor clusterChangeExecutor) {
    this.clusterChangeExecutor = clusterChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    final var partitionCount = partitionCount(currentPartitionGroupConfiguration);
    if (partitionCount > 0) {
      return Either.left(
          new IllegalStateException(
              "Cannot delete history as %d partitions still exist.".formatted(partitionCount)));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<PartitionGroupConfiguration>>();
    clusterChangeExecutor
        .deleteHistory()
        .onComplete(
            (ignore, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(UnaryOperator.identity());
              }
            });

    return result;
  }

  private int partitionCount(final PartitionGroupConfiguration group) {
    return (int)
        group.members().values().stream()
            .flatMap(m -> m.partitions().keySet().stream())
            .distinct()
            .count();
  }
}
