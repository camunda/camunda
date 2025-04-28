/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.*;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.stream.Stream;

public class ScaleUpRequestTransformer implements ConfigurationChangeRequest {

  private final int desiredPartitionCount;
  private final SortedSet<Integer> bootstrappedPartitions;

  public ScaleUpRequestTransformer(
      final int desiredPartitionCount, final SortedSet<Integer> bootstrappedPartitions) {
    this.desiredPartitionCount = desiredPartitionCount;
    this.bootstrappedPartitions = bootstrappedPartitions;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (desiredPartitionCount <= clusterConfiguration.partitionCount()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Cannot scale up when desiredPartitionCount %d is less than the current partition count %d",
                  desiredPartitionCount, clusterConfiguration.partitionCount())));
    }
    if (clusterConfiguration.partitionCount() + bootstrappedPartitions.size()
        != desiredPartitionCount) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Cannot start scale up: clusterConfiguration.partitionCount(%d) + bootstrappedPartitions(%d) != desiredPartitionCount(%d)",
                  clusterConfiguration.partitionCount(),
                  bootstrappedPartitions.size(),
                  desiredPartitionCount)));
    }
    final var operations =
        // Generate the operations for the "coordinator" node, which is the node with the lowest
        // id.
        // See ClusterConfigurationCoordinatorSupplier
        clusterConfiguration.members().entrySet().stream().min(Entry.comparingByKey()).stream()
            .map(Entry::getKey)
            .flatMap(
                id ->
                    Stream.of(
                        new StartPartitionScaleUp(id, desiredPartitionCount),
                        new AwaitRedistributionCompletion(
                            id, desiredPartitionCount, bootstrappedPartitions),
                        new AwaitRelocationCompletion(
                            id, desiredPartitionCount, bootstrappedPartitions)))
            .map(ClusterConfigurationChangeOperation.class::cast)
            .toList();
    if (operations.isEmpty()) {
      return Either.left(
          new InvalidRequest("Cannot scale up: no members registered in the configuration."));
    }
    return Either.right(operations);
  }
}
