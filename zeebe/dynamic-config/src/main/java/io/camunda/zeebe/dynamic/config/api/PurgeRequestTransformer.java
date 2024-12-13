/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public final class PurgeRequestTransformer implements ConfigurationChangeRequest {

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {

    final SortedMap<Integer, PartitionBootstrapOperation> primaries =
        findBootstrapMembers(clusterConfiguration.members());

    final Map<Integer, List<PartitionJoinOperation>> followers =
        new TreeMap<>(Comparator.naturalOrder());

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        operations.add(new PartitionLeaveOperation(memberId, partitionId));

        final var primaryForPartition = primaries.get(partitionId);

        if (!primaryForPartition.memberId().equals(memberId)) {
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

  /**
   * This method finds the leaders for each partition. Please note that when <a
   * href="https://github.com/camunda/camunda/issues/14786">order of priority in priority
   * election</a> is changed, this method must be updated.
   */
  private SortedMap<Integer, PartitionBootstrapOperation> findBootstrapMembers(
      final Map<MemberId, MemberState> members) {

    final SortedMap<Integer, PartitionBootstrapOperation> primaries =
        new TreeMap<>(Comparator.naturalOrder());

    members.forEach(
        (memberId, memberState) -> {
          memberState
              .partitions()
              .forEach(
                  (partitionId, partitionState) -> {
                    if (!primaries.containsKey(partitionId)
                        || primaries.get(partitionId).priority() < partitionState.priority()) {
                      primaries.put(
                          partitionId,
                          new PartitionBootstrapOperation(
                              memberId, partitionId, partitionState.priority()));
                    }
                  });
        });

    return primaries;
  }
}
