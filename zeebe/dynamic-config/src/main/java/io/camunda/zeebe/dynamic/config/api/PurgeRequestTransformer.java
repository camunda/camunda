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
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionPromoteOperation;
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

    final Map<Integer, List<ClusterConfigurationChangeOperation>> followers =
        new TreeMap<>(Comparator.naturalOrder());

    // Every leave except a partition's last is preceded by a demotion, so the removal commits
    // without the departing member. The last replica must not be demoted - a non-empty replication
    // group without a voting member could neither elect a leader nor commit - and keeps the
    // one-shot leave, which as the only remaining member it can drive to the empty configuration.
    final Map<Integer, Long> remainingActiveReplicas = new TreeMap<>();
    for (final var member : clusterConfiguration.members().values()) {
      member
          .partitions()
          .forEach(
              (partitionId, partition) -> {
                if (partition.state() == PartitionState.State.ACTIVE) {
                  remainingActiveReplicas.merge(partitionId, 1L, Long::sum);
                }
              });
    }

    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();
    for (final var member : clusterConfiguration.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        final var isActive = partitions.getValue().state() == PartitionState.State.ACTIVE;
        final var otherActiveReplicas =
            remainingActiveReplicas.getOrDefault(partitionId, 0L) - (isActive ? 1 : 0);
        if (otherActiveReplicas > 0) {
          operations.add(new PartitionDemoteOperation(memberId, partitionId));
        }
        if (isActive) {
          remainingActiveReplicas.merge(partitionId, -1L, Long::sum);
        }
        operations.add(new PartitionLeaveOperation(memberId, partitionId, 0));

        final var primaryForPartition = primaries.get(partitionId);

        if (!primaryForPartition.memberId().equals(memberId)) {
          final var partitionFollowers =
              followers.computeIfAbsent(partitionId, key -> new ArrayList<>());
          partitionFollowers.add(
              new PartitionJoinOperation(
                  memberId, partitionId, partitions.getValue().priority(), true));
          partitionFollowers.add(new PartitionPromoteOperation(memberId, partitionId));
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
