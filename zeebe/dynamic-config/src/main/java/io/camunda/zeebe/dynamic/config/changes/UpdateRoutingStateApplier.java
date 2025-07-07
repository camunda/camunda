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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

public class UpdateRoutingStateApplier implements ClusterOperationApplier {

  private final UpdateRoutingState updateRoutingState;
  private final PartitionScalingChangeExecutor executor;

  public UpdateRoutingStateApplier(
      final UpdateRoutingState updateRoutingState, final PartitionScalingChangeExecutor executor) {
    this.updateRoutingState = updateRoutingState;
    this.executor = executor;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var routingState =
        updateRoutingState.routingState().isPresent()
            ? CompletableActorFuture.completed(updateRoutingState.routingState().get())
            : executor.getRoutingState();

    return routingState.thenApply(
        state ->
            config -> {
              final var previousVersion =
                  config.routingState().map(RoutingState::version).orElse(0L);
              return config.setRoutingState(state.withVersion(previousVersion + 1));
            });
  }
}
