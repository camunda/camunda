/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.AwaitModeChangeOperation}, operating on a
 * single named {@link PartitionGroupConfiguration}. Mirrors the legacy {@code
 * AwaitModeChangeApplier} in {@code changes/}, which this does not replace or modify. Waits for a
 * member's partition manager to finish starting in the target {@link Mode}; changes no
 * configuration state, it only gates the change plan until the local transition has settled.
 */
public final class AwaitModeChangeApplier implements PartitionGroupConfigurationChangeApplier {

  private final Mode mode;
  private final ModeChangeExecutor modeChangeExecutor;

  public AwaitModeChangeApplier(final Mode mode, final ModeChangeExecutor modeChangeExecutor) {
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
