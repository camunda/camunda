/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PurgeRequestTransformer implements ConfigurationChangeRequest {

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {

    // TODO Maybe select new primary here to use down there
    final Map<Integer, PartitionBootstrapOperation> primaries = new HashMap<>();
    final Map<Integer, List<PartitionJoinOperation>> followers = new HashMap<>();

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        operations.add(new PartitionLeaveOperation(memberId, partitionId));

        if (!primaries.containsKey(partitionId)) {
          primaries.put(
              partitionId,
              new PartitionBootstrapOperation(
                  memberId, partitionId, partitions.getValue().priority()));
        } else {
          followers
              .computeIfAbsent(partitionId, key -> new ArrayList<>())
              .add(
                  new PartitionJoinOperation(
                      memberId, partitionId, partitions.getValue().priority()));
        }
      }
    }

    // TODO Delete history (only coordinator node)

    primaries.forEach(
        (partitionId, bootstrapOperation) -> {
          operations.add(bootstrapOperation);
        });
    followers.forEach(
        (partitionId, joinOperations) -> {
          operations.addAll(joinOperations);
        });

    return Either.right(operations);
  }
}
