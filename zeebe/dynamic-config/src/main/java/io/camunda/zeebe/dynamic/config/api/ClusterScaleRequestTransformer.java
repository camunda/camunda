/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ClusterScaleRequestTransformer implements ConfigurationChangeRequest {

  private final Supplier<PartitionDistributor> partitionDistributor;
  private final Optional<Integer> newClusterSize;
  private final Optional<Integer> newPartitionCount;
  private final Optional<Integer> newReplicationFactor;

  public ClusterScaleRequestTransformer(
      final Supplier<PartitionDistributor> partitionDistributor,
      final Optional<Integer> newClusterSize,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    this.partitionDistributor = partitionDistributor;
    this.newClusterSize = newClusterSize;
    this.newPartitionCount = newPartitionCount;
    this.newReplicationFactor = newReplicationFactor;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (newClusterSize.isEmpty() && newPartitionCount.isEmpty() && newReplicationFactor.isEmpty()) {
      // Nothing to change
      return Either.right(List.of());
    }

    // Count-based scaling generates bare node IDs without zone info, which breaks
    // zone-aware partition distributors. Reject until zone-aware scaling is implemented.
    if (clusterConfiguration.members().keySet().stream().anyMatch(m -> m.zone() != null)) {
      return Either.left(
          new InvalidRequest(
              "Scaling a zone-aware cluster by broker count is not supported."
                  + " Specify broker IDs explicitly instead."));
    }

    // replicationFactor and partitionCount is validated in the delegated transformer.

    final var newSetOfMembers =
        IntStream.range(0, newClusterSize.orElse(clusterConfiguration.members().size()))
            .mapToObj(i -> MemberId.from(String.valueOf(i)))
            .collect(Collectors.toSet());
    return new ScaleRequestTransformer(
            partitionDistributor, newSetOfMembers, newReplicationFactor, newPartitionCount)
        .operations(clusterConfiguration);
  }
}
