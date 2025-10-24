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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class AwaitRedistributionCompletionApplier implements ClusterOperationApplier {

  private final PartitionScalingChangeExecutor executor;
  private final AwaitRedistributionCompletion operation;

  public AwaitRedistributionCompletionApplier(
      final PartitionScalingChangeExecutor executor,
      final AwaitRedistributionCompletion operation) {
    this.executor = Objects.requireNonNull(executor);
    this.operation = Objects.requireNonNull(operation);
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {

    if (operation.partitionsToRedistribute().isEmpty()) {
      return Either.left(
          new IllegalArgumentException("Cannot activate partitions: empty list provided"));
    }
    if (currentClusterConfiguration.routingState().isEmpty()) {
      return Either.left(
          new IllegalStateException(
              "Routing state is not initialized yet, cannot include partitions in request handling."));
    }

    if (currentClusterConfiguration.routingState().get().requestHandling()
        instanceof AllPartitions) {
      return Either.left(
          new IllegalStateException(
              "All partitions are active, cannot include partitions in request handling."));
    }
    return Either.right(UnaryOperator.identity());
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();
    executor
        .awaitRedistributionCompletion(
            operation.desiredPartitionCount(), operation.partitionsToRedistribute(), null)
        .onComplete(
            (ignored, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(this::updateRequestHandling);
              }
            });
    return result;
  }

  private ClusterConfiguration updateRequestHandling(final ClusterConfiguration config) {
    final var routingState =
        config
            .routingState()
            .map(state -> state.withRequestHandling(this::updateRequestHandling))
            .orElseThrow(() -> new IllegalStateException("Missing routingState"));
    return new ClusterConfiguration(
        config.version(),
        config.members(),
        config.lastChange(),
        config.pendingChanges(),
        Optional.of(routingState),
        config.clusterId());
  }

  private RequestHandling updateRequestHandling(final RequestHandling requestHandling) {
    return switch (requestHandling) {
      case final ActivePartitions activePartitions ->
          activePartitions.activatePartitions(operation.partitionsToRedistribute());
      case final AllPartitions ignored ->
          throw new IllegalStateException("Cannot be AllPartitions");
    };
  }
}
