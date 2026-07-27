/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NullMarked;

/**
 * New-model applier for {@code GlobalChangeOperation.UpdatePartitionDistributorConfigOperation},
 * operating on {@link GlobalConfiguration}. Mirrors the legacy {@code
 * UpdatePartitionDistributorConfigApplier} in {@code changes/}, which this does not replace or
 * modify.
 */
@NullMarked
public final class UpdatePartitionDistributorConfigApplier
    implements GlobalConfigurationChangeApplier {

  private final UpdatePartitionDistributorConfigOperation operation;

  public UpdatePartitionDistributorConfigApplier(
      final UpdatePartitionDistributorConfigOperation operation) {
    this.operation = operation;
  }

  @Override
  public Either<Exception, UnaryOperator<GlobalConfiguration>> init(
      final CurrentClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<GlobalConfiguration>> apply() {
    return CompletableActorFuture.completed(
        config -> config.setPartitionDistributorConfig(operation.config()));
  }
}
