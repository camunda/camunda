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
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Add new partitions and reassign all partitions to the given members based on round-robin
 * strategy.
 */
public class PartitionReassignRequestTransformer implements ConfigurationChangeRequest {
  final Set<MemberId> members;
  private final Optional<Integer> newReplicationFactor;
  private final Optional<Integer> newPartitionCount;

  public PartitionReassignRequestTransformer(
      final Set<MemberId> members,
      final Optional<Integer> newReplicationFactor,
      final Optional<Integer> newPartitionCount) {
    this.members = members;
    this.newReplicationFactor = newReplicationFactor;
    this.newPartitionCount = newPartitionCount;
  }

  public PartitionReassignRequestTransformer(final Set<MemberId> members) {
    this(members, Optional.empty(), Optional.empty());
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }

    return generatePartitionDistributionOperations(clusterConfiguration, members);
  }

  public Either<Exception, List<ClusterConfigurationChangeOperation>> newOperations(
      final ClusterConfiguration clusterConfiguration,
      final Set<PartitionMetadata> newPartitionDistribution) {
    if (members.isEmpty()) {
      return Either.left(
          new InvalidRequest(
              new IllegalArgumentException(
                  "Cannot reassign partitions if no brokers are provided")));
    }

    return newGeneratePartitionDistributionOperations(
        clusterConfiguration, members, newPartitionDistribution);
  }

  private int getReplicationFactor(final ClusterConfiguration clusterConfiguration) {
    return newReplicationFactor.orElse(clusterConfiguration.minReplicationFactor());
  }

  private int getPartitionCount(final ClusterConfiguration clusterConfiguration) {
    return newPartitionCount.orElse(clusterConfiguration.partitionCount());
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>>
      generatePartitionDistributionOperations(
          final ClusterConfiguration currentConfiguration, final Set<MemberId> brokers) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var oldDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(currentConfiguration, "temp").stream()
            .collect(Collectors.toMap(PartitionMetadata::id, p -> p));

    final int partitionCount = getPartitionCount(currentConfiguration);
    if (partitionCount < currentConfiguration.partitionCount()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "New partition count [%d] must be greater than or equal to the current partition count [%d]",
                  partitionCount, currentConfiguration.partitionCount())));
    }

    final int replicationFactor = getReplicationFactor(currentConfiguration);
    if (replicationFactor <= 0) {
      return Either.left(
          new InvalidRequest(
              String.format("Replication factor [%d] must be greater than 0", replicationFactor)));
    }

    if (brokers.size() < replicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Number of brokers [%d] is less than the replication factor [%d]",
                  brokers.size(), replicationFactor)));
    }

    final var coordinatorNodeId =
        ClusterConfigurationCoordinatorSupplier.of(() -> currentConfiguration)
            .getDefaultCoordinator();

    final List<PartitionId> oldPartitions = oldDistribution.keySet().stream().sorted().toList();

    final List<PartitionId> newPartitions =
        newPartitionCount
            .map(
                n ->
                    IntStream.rangeClosed(currentConfiguration.partitionCount() + 1, n)
                        .mapToObj(i -> PartitionId.from("temp", i))
                        .sorted()
                        .toList())
            .orElse(List.of());

    final PartitionDistributor roundRobinDistributor = new RoundRobinPartitionDistributor();

    final var allPartitions =
        Stream.of(oldPartitions, newPartitions).flatMap(List::stream).toList();

    final var newDistribution =
        roundRobinDistributor
            .distributePartitions(brokers, allPartitions, replicationFactor)
            .stream()
            .collect(Collectors.toMap(PartitionMetadata::id, p -> p));

    for (final PartitionId partition : oldPartitions) {
      final var newMetadata = newDistribution.get(partition);
      final var oldMetadata = oldDistribution.get(partition);
      operations.addAll(movePartition(oldMetadata, newMetadata));
    }
    final var hasNewPartitions = !newPartitions.isEmpty();

    if (hasNewPartitions) {
      operations.add(new StartPartitionScaleUp(coordinatorNodeId, newPartitionCount.get()));
      for (final PartitionId partition : newPartitions) {
        final var newMetadata = newDistribution.get(partition);
        operations.addAll(addPartition(newMetadata));
      }
      operations.addAll(
          List.of(
              new AwaitRedistributionCompletion(
                  coordinatorNodeId,
                  newPartitionCount.get(),
                  new TreeSet<>(newPartitions.stream().map(PartitionId::id).toList())),
              new AwaitRelocationCompletion(
                  coordinatorNodeId,
                  newPartitionCount.get(),
                  new TreeSet<>(newPartitions.stream().map(PartitionId::id).toList()))));
    }

    return Either.right(operations);
  }

  private Either<Exception, List<ClusterConfigurationChangeOperation>>
      newGeneratePartitionDistributionOperations(
          final ClusterConfiguration currentConfiguration,
          final Set<MemberId> brokers,
          final Set<PartitionMetadata> newPartitionDistribution) {
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var oldDistribution =
        ConfigurationUtil.getPartitionDistributionFrom(currentConfiguration, "temp");

    final int partitionCount = getPartitionCount(currentConfiguration);
    if (partitionCount < currentConfiguration.partitionCount()) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "New partition count [%d] must be greater than or equal to the current partition count [%d]",
                  partitionCount, currentConfiguration.partitionCount())));
    }

    final int replicationFactor = getReplicationFactor(currentConfiguration);
    if (replicationFactor <= 0) {
      return Either.left(
          new InvalidRequest(
              String.format("Replication factor [%d] must be greater than 0", replicationFactor)));
    }

    if (brokers.size() < replicationFactor) {
      return Either.left(
          new InvalidRequest(
              String.format(
                  "Number of brokers [%d] is less than the replication factor [%d]",
                  brokers.size(), replicationFactor)));
    }

    // Can bootstrap partitions only in the sorted order
    final var sortedPartitionMetadata =
        newPartitionDistribution.stream()
            .sorted(Comparator.comparingInt(p -> p.id().id()))
            .toList();
    for (final PartitionMetadata newMetadata : sortedPartitionMetadata) {
      oldDistribution.stream()
          .filter(old -> old.id().id().equals(newMetadata.id().id()))
          .findFirst()
          .ifPresentOrElse(
              oldMetadata -> operations.addAll(movePartition(oldMetadata, newMetadata)),
              () -> operations.addAll(addPartition(newMetadata)));
    }

    return Either.right(operations);
  }

  private List<ClusterConfigurationChangeOperation> addPartition(
      final PartitionMetadata newMetadata) {
    final Integer partitionId = newMetadata.id().id();
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    // Bootstrap the partition in the primary
    final var primary =
        newMetadata.getPrimary().orElse(newMetadata.members().stream().findAny().orElseThrow());
    operations.add(
        new PartitionBootstrapOperation(
            primary, partitionId, newMetadata.getPriority(primary), true));

    // Join each remaining members to the partition
    for (final MemberId member : newMetadata.members()) {
      if (!member.equals(primary)) {
        operations.add(
            new PartitionJoinOperation(member, partitionId, newMetadata.getPriority(member)));
      }
    }

    return operations;
  }

  private List<ClusterConfigurationChangeOperation> movePartition(
      final PartitionMetadata oldMetadata, final PartitionMetadata newMetadata) {
    final Integer partitionId = newMetadata.id().id();
    final List<ClusterConfigurationChangeOperation> operations = new ArrayList<>();

    final var membersToJoin =
        newMetadata.members().stream()
            .filter(member -> !oldMetadata.members().contains(member))
            .map(
                newMember ->
                    new PartitionJoinOperation(
                        newMember, partitionId, newMetadata.getPriority(newMember)))
            .toList();
    final var membersToLeave =
        oldMetadata.members().stream()
            .filter(member -> !newMetadata.members().contains(member))
            .map(oldMember -> new PartitionLeaveOperation(oldMember, partitionId, 1))
            .toList();
    final var membersToChangePriority =
        oldMetadata.members().stream()
            .filter(memberId -> newMetadata.members().contains(memberId))
            .filter(
                memberId -> newMetadata.getPriority(memberId) != oldMetadata.getPriority(memberId))
            .map(
                memberId ->
                    new PartitionReconfigurePriorityOperation(
                        memberId, partitionId, newMetadata.getPriority(memberId)))
            .toList();

    // TODO: interleave join and leave operation
    operations.addAll(membersToJoin);
    operations.addAll(membersToLeave);
    operations.addAll(membersToChangePriority);
    return operations;
  }
}
