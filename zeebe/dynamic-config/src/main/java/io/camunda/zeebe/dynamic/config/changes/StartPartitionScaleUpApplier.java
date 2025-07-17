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
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.ActivePartitions;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling.AllPartitions;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.Either.Left;
import io.camunda.zeebe.util.Either.Right;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Starts the process of scaling up partitions. The routing state version is incremented and the
 * request handling strategy is updated from {@link AllPartitions} to {@link ActivePartitions} one,
 * with new partitions starting off as inactive.
 *
 * <p>We use the request handling strategy to detect whether scaling up is possible or not - we
 * expect {@link AllPartitions} at the beginning, update it to {@link ActivePartitions} and finish
 * the scaling by going back to {@link AllPartitions}. If we find anything but {@link
 * AllPartitions}, we assume that we are already scaling up and refuse the operation.
 */
final class StartPartitionScaleUpApplier implements ClusterOperationApplier {

  private final PartitionScalingChangeExecutor partitionScalingChangeExecutor;
  private final int desiredPartitionCount;

  StartPartitionScaleUpApplier(
      final PartitionScalingChangeExecutor partitionScalingChangeExecutor,
      final int desiredPartitionCount) {
    this.partitionScalingChangeExecutor = partitionScalingChangeExecutor;
    this.desiredPartitionCount = desiredPartitionCount;
  }

  @Override
  public Either<Exception, UnaryOperator<ClusterConfiguration>> init(
      final ClusterConfiguration currentClusterConfiguration) {
    if (desiredPartitionCount < Protocol.START_PARTITION_ID) {
      return new Left<>(
          new IllegalArgumentException(
              "Desired partition count must be greater than %d"
                  .formatted(Protocol.START_PARTITION_ID)));
    }

    if (desiredPartitionCount > Protocol.MAXIMUM_PARTITIONS) {
      return new Left<>(
          new IllegalArgumentException(
              "Desired partition count must not exceed %d".formatted(Protocol.MAXIMUM_PARTITIONS)));
    }

    if (desiredPartitionCount <= currentClusterConfiguration.partitionCount()) {
      return new Left<>(
          new IllegalStateException(
              "Desired partition count (%d) must be greater than current partition count(%d)"
                  .formatted(desiredPartitionCount, currentClusterConfiguration.partitionCount())));
    }

    if (currentClusterConfiguration.routingState().isEmpty()) {
      return new Left<>(
          new IllegalStateException(
              "Routing state is not initialized yet, scaling up is not possible."));
    }
    final var routing = currentClusterConfiguration.routingState().get();
    final var requestHandling = routing.requestHandling();

    return switch (requestHandling) {
      case AllPartitions(final var currentPartitionCount) -> {
        if (desiredPartitionCount <= currentPartitionCount) {
          yield new Left<>(
              new IllegalStateException(
                  "Already routing to %d partitions, can't scale down to %d"
                      .formatted(currentPartitionCount, desiredPartitionCount)));
        } else {
          yield new Right<>(UnaryOperator.identity());
        }
      }
      default ->
          new Left<>(
              new IllegalStateException(
                  "Cannot start scaling up because request handling strategy is not stable: %s"
                      .formatted(requestHandling)));
    };
  }

  @Override
  public ActorFuture<UnaryOperator<ClusterConfiguration>> apply() {
    final var result = new CompletableActorFuture<UnaryOperator<ClusterConfiguration>>();

    partitionScalingChangeExecutor
        .initiateScaleUp(desiredPartitionCount)
        .onComplete(
            (ignoredResult, error) -> {
              if (error != null) {
                result.completeExceptionally(error);
              } else {
                result.complete(
                    config ->
                        new ClusterConfiguration(
                            config.version(),
                            config.members(),
                            config.lastChange(),
                            config.pendingChanges(),
                            config.routingState().map(this::updateRoutingState),
                            config.clusterId()));
              }
            });

    return result;
  }

  private RoutingState updateRoutingState(final RoutingState routingState) {
    return new RoutingState(
        routingState.version() + 1,
        updateRequestHandling(routingState.requestHandling()),
        routingState.messageCorrelation());
  }

  private RequestHandling updateRequestHandling(final RequestHandling requestHandling) {
    return switch (requestHandling) {
      case AllPartitions(final var currentPartitionCount) -> {
        final var newPartitions =
            IntStream.rangeClosed(currentPartitionCount + 1, desiredPartitionCount)
                .boxed()
                .collect(Collectors.toSet());
        yield new ActivePartitions(currentPartitionCount, Set.of(), newPartitions);
      }
      case final ActivePartitions activePartitions ->
          throw new IllegalStateException(
              "Unexpected request handling state: %s".formatted(activePartitions));
    };
  }
}
