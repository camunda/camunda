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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.function.UnaryOperator;

public class AwaitRelocationCompletionApplier implements ClusterOperationApplier {

  private final PartitionScalingChangeExecutor executor;
  private final AwaitRelocationCompletion operation;

  public AwaitRelocationCompletionApplier(
      final PartitionScalingChangeExecutor executor, final AwaitRelocationCompletion operation) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {

    if (operation.partitionsToRelocate().isEmpty()) {
      return Either.left(
          new IllegalArgumentException("Cannot activate partitions: empty list provided"));
    }
    if (currentClusterConfiguration.routingState().isEmpty()) {
      return Either.left(
          new IllegalStateException(
              "Routing state is not initialized yet, cannot include partitions in request handling."));
    }

    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    return CompletableActorFuture.completed(UnaryOperator.identity());
  }
}
