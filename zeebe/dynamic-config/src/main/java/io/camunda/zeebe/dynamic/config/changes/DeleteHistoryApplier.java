/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

final class DeleteHistoryApplier implements ClusterOperationApplier {
  private final ClusterChangeExecutor clusterChangeExecutor;

  public DeleteHistoryApplier(
      final MemberId memberId, final ClusterChangeExecutor clusterChangeExecutor) {
    this.clusterChangeExecutor = clusterChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    if (currentClusterConfiguration.partitionCount() > 0) {
      return Either.left(
          new IllegalStateException(
              "Cannot delete history as "
                  + currentClusterConfiguration.partitionCount()
                  + " partitions still exist."));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
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
}
