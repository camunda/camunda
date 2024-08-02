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

public class RelocationStartApplier implements ClusterOperationApplier {

  private final int oldPartitionCount;
  private final int newPartitionCount;
  private final MemberId memberId;
  private final PartitionChangeExecutor partitionChangeExecutor;

  public RelocationStartApplier(
      final int oldPartitionCount,
      final int newPartitionCount,
      final MemberId memberId,
      final PartitionChangeExecutor partitionChangeExecutor) {
    this.oldPartitionCount = oldPartitionCount;
    this.newPartitionCount = newPartitionCount;
    this.memberId = memberId;
    this.partitionChangeExecutor = partitionChangeExecutor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var future = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
    partitionChangeExecutor
        .startRelocation(oldPartitionCount, newPartitionCount)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                // TODO: May be update the state as relocating for observability
                future.complete(UnaryOperator.identity());
              } else {
                future.completeExceptionally(error);
              }
            });
    return future;
  }
}
