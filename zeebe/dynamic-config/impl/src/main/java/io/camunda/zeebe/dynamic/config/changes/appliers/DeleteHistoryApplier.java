/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
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
 * <p>History deletion is executed through the {@link PartitionChangeExecutor} of this partition
 * group, so that only the history exported by this group is purged.
 */
public final class DeleteHistoryApplier implements PartitionGroupConfigurationChangeApplier {

  private final PartitionChangeExecutor partitionChangeExecutor;

  public DeleteHistoryApplier(final PartitionChangeExecutor partitionChangeExecutor) {
    this.partitionChangeExecutor = partitionChangeExecutor;
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
    partitionChangeExecutor
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
