/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.changes.appliers;

import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeApplier;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.function.UnaryOperator;

/**
 * New-model applier for {@code PartitionGroupOperation.UpdateRoutingState}, operating on a single
 * named {@link PartitionGroupConfiguration} as a whole. Mirrors the legacy {@code
 * UpdateRoutingStateApplier} in {@code changes/}, which this does not replace or modify.
 */
public final class UpdateRoutingStateApplier implements PartitionGroupConfigurationChangeApplier {

  private final UpdateRoutingState updateRoutingState;
  private final PartitionScalingChangeExecutor executor;

  public UpdateRoutingStateApplier(
      final UpdateRoutingState updateRoutingState, final PartitionScalingChangeExecutor executor) {
    this.updateRoutingState = updateRoutingState;
    this.executor = executor;
  }

  @Override
  public Either<Exception, UnaryOperator<PartitionGroupConfiguration>> init(
      final GlobalConfiguration currentGlobalConfiguration,
      final PartitionGroupConfiguration currentPartitionGroupConfiguration) {
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<PartitionGroupConfiguration>> apply() {
    final var routingState =
        updateRoutingState.routingState().isPresent()
            ? CompletableActorFuture.completed(updateRoutingState.routingState().get())
            : executor.getRoutingState();

    return routingState.thenApply(
        state -> {
          if (state == null) {
            return UnaryOperator.identity();
          } else {
            return (UnaryOperator<PartitionGroupConfiguration>)
                group -> {
                  final var previousVersion =
                      group.routingState().map(RoutingState::version).orElse(0L);
                  return group.setRoutingState(state.withVersion(previousVersion + 1));
                };
          }
        });
  }
}
