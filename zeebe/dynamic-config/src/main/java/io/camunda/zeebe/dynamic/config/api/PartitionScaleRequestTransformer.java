/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PartitionScaleRequestTransformer implements ConfigurationChangeRequest {

  private final int partitionsCount;

  public PartitionScaleRequestTransformer(final int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    // TODO: check preconditions - partitionsToAdd are contiguous to existing partitionIds,
    // replicationFactor vs brokerSize, existing replicationFactor

    final var replicationFactor = clusterConfiguration.minReplicationFactor();
    final var brokers = clusterConfiguration.members().keySet();
    final var previousPartitionsCount = clusterConfiguration.partitionCount();
    final var sortedPartitions =
        IntStream.rangeClosed(1, partitionsCount)
            .mapToObj(i -> PartitionId.from("temp", i))
            .sorted()
            .toList();

    final var partitionsToAdd =
        sortedPartitions.stream()
            .map(PartitionId::id)
            .filter(id -> id > previousPartitionsCount)
            .collect(Collectors.toSet());

    final PartitionDistributor roundRobinDistributor = new RoundRobinPartitionDistributor();

    final var newDistribution =
        roundRobinDistributor.distributePartitions(brokers, sortedPartitions, replicationFactor);

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    for (final var partitionMetadata : newDistribution) {
      final var partitionId = partitionMetadata.id().id();
      if (!partitionsToAdd.contains(partitionId)) {
        continue;
      }

      // Bootstrap primary replica
      final MemberId primary = partitionMetadata.getPrimary().get();
      operations.add(
          new ClusterConfigurationChangeOperation.PartitionChangeOperation
              .PartitionBootstrapOperation(
              primary, partitionId, partitionMetadata.getPriority(primary)));

      // then join rest of the members
      partitionMetadata.members().stream()
          .filter(m -> !m.equals(primary))
          .forEach(
              m ->
                  operations.add(
                      new ClusterConfigurationChangeOperation.PartitionChangeOperation
                          .PartitionJoinOperation(
                          m, partitionId, partitionMetadata.getPriority(m))));

      // allow routing requests to partition
      operations.add(
          new ClusterConfigurationChangeOperation.RoutingAddPartitionOperation(
              primary, partitionId));
    }

    return Either.right(operations);
  }
}
