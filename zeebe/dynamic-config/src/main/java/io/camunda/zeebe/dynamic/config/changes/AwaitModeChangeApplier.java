/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.ClusterOperationApplier;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Waits for a member's partition manager to finish starting in the target {@link Mode}. Emitted
 * after the per-member {@code ModeChangeOperation}s so that the cluster change only completes once
 * every member's partitions are actually up. It changes no cluster configuration state; it only
 * gates the change plan until the local transition has settled.
 */
public class AwaitModeChangeApplier implements ClusterOperationApplier {

  private final Mode mode;
  private final ModeChangeExecutor modeChangeExecutor;

  public AwaitModeChangeApplier(final Mode mode, final ModeChangeExecutor modeChangeExecutor) {
    this.mode = Objects.requireNonNull(mode);
    this.modeChangeExecutor = Objects.requireNonNull(modeChangeExecutor);
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
    modeChangeExecutor
        .awaitModeApplied(mode)
        .onComplete(
            (ignored, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(UnaryOperator.identity());
              }
            });
    return result;
  }
}
