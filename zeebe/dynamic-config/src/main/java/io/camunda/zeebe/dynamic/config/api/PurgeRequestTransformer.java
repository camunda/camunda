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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public final class PurgeRequestTransformer implements ConfigurationChangeRequest {

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {

    final var firstMember = clusterConfiguration.members().keySet().stream().findFirst();
    if (firstMember.isEmpty()) {
      return Either.right(new ArrayList<>());
    }

    final SortedMap<Integer, PartitionBootstrapOperation> primaries =
        createBootstrapOperations(clusterConfiguration.members());

    final Map<Integer, List<PartitionJoinOperation>> followers =
        new TreeMap<>(Comparator.naturalOrder());

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        operations.add(new PartitionLeaveOperation(memberId, partitionId, 0));

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

    operations.add(new DeleteHistoryOperation(firstMember.get()));
    operations.add(new UpdateIncarnationNumberOperation(firstMember.get()));

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

  /** This method creates the BootstrapOperations for all leaders for each partition. */
  private SortedMap<Integer, PartitionBootstrapOperation> createBootstrapOperations(
      final Map<MemberId, MemberState> members) {

    final SortedMap<Integer, PartitionBootstrapOperation> primaries =
        new TreeMap<>(Comparator.naturalOrder());

    members.forEach(
        (memberId, memberState) -> {
          memberState.partitions().forEach(createBootstrapOperation(memberId, primaries));
        });

    return primaries;
  }

  private BiConsumer<Integer, PartitionState> createBootstrapOperation(
      final MemberId memberId, final SortedMap<Integer, PartitionBootstrapOperation> primaries) {
    return (partitionId, partitionState) -> {
      if (!primaries.containsKey(partitionId)
          || partitionState.hasHigherPriority(primaries.get(partitionId).priority())) {
        primaries.put(
            partitionId,
            new PartitionBootstrapOperation(
                memberId,
                partitionId,
                partitionState.priority(),
                Optional.of(partitionState.config()),
                false));
      }
    };
  }
}
