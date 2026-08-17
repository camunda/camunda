/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.InvalidRequest;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.PartitionReassignmentOperationsGenerator;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Plans a partition-count scale-up of one physical tenant's partition group, for {@link
 * ClusterScaleRequestTransformer} and {@link ClusterPatchRequestTransformer}.
 *
 * <p>Partitions are distributed across brokers considering every physical tenant's partitions at
 * once, not just those of the tenant being scaled: a tenant scaled on its own would be placed as if
 * the brokers held nothing else, and would pile onto brokers that are already busy with another
 * tenant's partitions. This is why the change cannot be planned through {@code
 * ConfigurationChangeRequest#operations(ClusterConfiguration)} — the legacy configuration that
 * takes projects a single partition group, so a distribution computed from it can only ever see the
 * partitions of the group being scaled.
 *
 * <p>The distribution itself is the cluster's configured {@link
 * io.camunda.zeebe.dynamic.config.PartitionDistributor} — the same round-robin (or zone-aware) one
 * {@code PartitionReassignRequestTransformer} already applies, only over every group's partitions
 * rather than one group's. It computes a placement from scratch, so scaling one tenant can also
 * relocate partitions of another; a {@code PartitionReassigner} that reaches a comparable balance
 * while moving fewer existing partitions can replace this one call without changing anything else
 * here.
 *
 * <p>{@link PartitionReassignmentOperationsGenerator} turns the resulting distribution into
 * operations per group, emitting them only where a partition's placement actually changed.
 */
final class PartitionGroupScalingPhases {

  private PartitionGroupScalingPhases() {}

  /**
   * @param targetGroupId the partition group to scale; must exist in {@code clusterConfiguration}
   * @param clusterConfiguration the current multi-group configuration
   * @param newPartitionCount the partition count {@code targetGroupId} should reach
   * @return a single {@link PartitionGroupParallelPhase} covering every group whose partitions have
   *     to change, or no phase at all when the group already has {@code newPartitionCount}
   *     partitions. Left if the target count is below the group's current one — partitions can only
   *     be scaled up — or if no valid distribution exists for it.
   */
  static Either<Exception, List<Phase>> phases(
      final String targetGroupId,
      final CurrentClusterConfiguration clusterConfiguration,
      final int newPartitionCount) {
    final var distributionByGroup =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(clusterConfiguration);
    final var currentPartitionCount =
        distributionByGroup.getOrDefault(targetGroupId, Set.of()).size();

    if (newPartitionCount < currentPartitionCount) {
      return Either.left(
          new InvalidRequest(
              "New partition count [%d] of physical tenant '%s' must be greater than or equal to its current partition count [%d]"
                  .formatted(newPartitionCount, targetGroupId, currentPartitionCount)));
    }
    if (newPartitionCount == currentPartitionCount) {
      return Either.right(List.of());
    }

    final var newPartitionIds =
        IntStream.rangeClosed(currentPartitionCount + 1, newPartitionCount)
            .mapToObj(number -> new PartitionId(targetGroupId, number))
            .toList();
    final var targetPartitionIds =
        Stream.concat(
                distributionByGroup.values().stream()
                    .flatMap(Set::stream)
                    .map(PartitionMetadata::id),
                newPartitionIds.stream())
            .toList();
    final var replicationFactor = replicationFactor(distributionByGroup);

    final Map<String, List<PartitionGroupOperation>> operationsByGroup;
    try {
      final var targetDistribution =
          targetDistribution(
              clusterConfiguration,
              clusterConfiguration.liveMembers(),
              targetPartitionIds,
              replicationFactor);
      operationsByGroup =
          new LinkedHashMap<>(
              PartitionReassignmentOperationsGenerator.generateOperations(
                  clusterConfiguration, targetDistribution, Map.of()));
    } catch (final RuntimeException e) {
      // What the distributor and the generator raise is a rejection of the request, not an internal
      // failure: too few live brokers for the replication factor, a distributor that cannot place
      // these partitions, a distribution that would drop one.
      return Either.left(new InvalidRequest(e));
    }

    final var newPartitionNumbers =
        new TreeSet<>(newPartitionIds.stream().map(PartitionId::number).toList());
    operationsByGroup.put(
        targetGroupId,
        withScaleUpOperations(
            operationsByGroup.getOrDefault(targetGroupId, List.of()),
            ClusterConfigurationCoordinatorSupplier.from(() -> clusterConfiguration)
                .getDefaultCoordinator(),
            newPartitionCount,
            newPartitionNumbers));

    return Either.right(List.of(new PartitionGroupParallelPhase(operationsByGroup)));
  }

  private static Set<PartitionMetadata> targetDistribution(
      final CurrentClusterConfiguration clusterConfiguration,
      final Set<MemberId> targetMembers,
      final List<PartitionId> targetPartitionIds,
      final int replicationFactor) {
    return clusterConfiguration
        .globalConfiguration()
        .partitionDistributorConfig()
        .map(PartitionDistributorConfig::toDistributor)
        .orElseGet(RoundRobinPartitionDistributor::new)
        .distributePartitions(
            targetMembers, targetPartitionIds.stream().sorted().toList(), replicationFactor);
  }

  /**
   * The replication factor every partition should keep. Taken as the minimum currently in use
   * rather than a configured value, mirroring {@code ClusterConfiguration#minReplicationFactor}:
   * during a configuration change a partition can temporarily hold more replicas than the cluster
   * is configured for, and treating that as the target would permanently widen it.
   */
  private static int replicationFactor(
      final Map<String, Set<PartitionMetadata>> distributionByGroup) {
    return distributionByGroup.values().stream()
        .flatMap(Set::stream)
        .mapToInt(metadata -> metadata.members().size())
        .min()
        .orElse(1);
  }

  /**
   * Wraps the scaled group's partition operations in the three {@code ScaleUpOperation}s that drive
   * the engine's side of a scale-up: the engine is told the new partition count before any new
   * partition is bootstrapped, and redistribution and relocation are awaited once they all are.
   * Mirrors the ordering {@code PartitionReassignRequestTransformer} produces for the legacy
   * single-group model.
   *
   * <p>All three name the cluster configuration coordinator rather than a member of the scaled
   * group. They do not act on a local partition — they drive the group's engine through a
   * group-scoped {@code BrokerClient} request — and every broker registers change appliers for
   * every configured physical tenant, whether or not it holds any of that tenant's partitions.
   */
  private static List<PartitionGroupOperation> withScaleUpOperations(
      final List<PartitionGroupOperation> groupOperations,
      final MemberId coordinator,
      final int newPartitionCount,
      final SortedSet<Integer> newPartitionNumbers) {
    final var operations = new ArrayList<PartitionGroupOperation>();
    groupOperations.stream()
        .filter(operation -> !isForNewPartition(operation, newPartitionNumbers))
        .forEach(operations::add);
    operations.add(new StartPartitionScaleUp(coordinator, newPartitionCount));
    groupOperations.stream()
        .filter(operation -> isForNewPartition(operation, newPartitionNumbers))
        .forEach(operations::add);
    operations.add(
        new AwaitRedistributionCompletion(coordinator, newPartitionCount, newPartitionNumbers));
    operations.add(
        new AwaitRelocationCompletion(coordinator, newPartitionCount, newPartitionNumbers));
    return operations;
  }

  private static boolean isForNewPartition(
      final PartitionGroupOperation operation, final Set<Integer> newPartitionNumbers) {
    return operation instanceof final PartitionChangeOperation partitionChange
        && newPartitionNumbers.contains(partitionChange.partitionId());
  }
}
