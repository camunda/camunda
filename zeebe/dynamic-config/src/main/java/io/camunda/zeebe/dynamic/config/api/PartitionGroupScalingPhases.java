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
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.dynamic.config.util.PartitionReassignmentOperationsGenerator;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Plans a change to where the cluster's partitions live, for {@link
 * ClusterScaleRequestTransformer}, {@link ClusterPatchRequestTransformer} and {@link
 * ScaleRequestTransformer}: a partition-count scale-up of one physical tenant's partition group, a
 * change of the cluster-wide replication factor, a change of the cluster's membership, or any
 * combination of them.
 *
 * <p>Partitions are distributed across brokers considering every physical tenant's partitions at
 * once, not just those of the tenant being scaled: a tenant scaled on its own would be placed as if
 * the brokers held nothing else, and would pile onto brokers that are already busy with another
 * tenant's partitions. This is why the placement is computed from the whole {@link
 * CurrentClusterConfiguration} rather than from one group: a distribution computed from a single
 * group can only ever see that group's partitions. The replication factor is why this matters even
 * when no partition count changes: it is a cluster-wide setting, so it has to reach every group's
 * partitions rather than only the default group's.
 *
 * <p>The distribution itself is the cluster's configured {@link
 * io.camunda.zeebe.dynamic.config.PartitionDistributor}, the round-robin or zone-aware one, applied
 * over every group's partitions at once. It computes a placement from scratch, so scaling one
 * tenant can also relocate partitions of another; a {@code PartitionReassigner} that reaches a
 * comparable balance while moving fewer existing partitions can replace this one call without
 * changing anything else here.
 *
 * <p>{@link PartitionReassignmentOperationsGenerator} turns the resulting distribution into
 * operations per group, emitting them only where a partition's placement actually changed.
 *
 * <p>Every group of the given configuration is planned for, disabled physical tenants included —
 * the coordinator removes those before it calls {@code ConfigurationChangeRequest#phases} (see
 * {@code ConfigurationChangeRequest#applyToDisabledTenants}), so a group that reaches this planner
 * is one that runs on a broker and can apply an operation. No filter is needed here.
 */
final class PartitionGroupScalingPhases {

  private PartitionGroupScalingPhases() {}

  /**
   * Plans the placement over the cluster's live members, for a request that does not change
   * membership.
   *
   * @param targetGroupId the partition group whose partition count changes; must exist in {@code
   *     clusterConfiguration} whenever {@code newPartitionCount} is present, and is unused
   *     otherwise — the replication factor has no target group
   * @param clusterConfiguration the current multi-group configuration
   * @param newPartitionCount the partition count {@code targetGroupId} should reach, or empty to
   *     leave every group's partition count as it is
   * @param newReplicationFactor the replication factor every partition of every group should reach,
   *     or empty to keep the one currently in use
   * @return a single {@link PartitionGroupPhase} covering every group whose partitions have to
   *     change, or no phase at all when the cluster already has the requested placement. Left if
   *     the target count is below the group's current one — partitions can only be scaled up — if
   *     the live brokers cannot satisfy the replication factor, or if no valid distribution exists.
   */
  static Either<Exception, List<Phase>> phases(
      final String targetGroupId,
      final CurrentClusterConfiguration clusterConfiguration,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    final var distributionByGroup =
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(clusterConfiguration);
    final var currentPartitionCount = currentPartitionCount(distributionByGroup, targetGroupId);
    if (newReplicationFactor.isEmpty()
        && newPartitionCount.orElse(currentPartitionCount) == currentPartitionCount) {
      // Nothing is asked for that the cluster does not already have. Returning before the
      // distributor runs is what keeps such a request a no-op: a placement that has drifted from
      // what the distributor would compute now — after a manual reassignment through
      // /partition-distribution, say — would otherwise be rebalanced by a request that asked for
      // no change at all. It also keeps a request that asks for nothing answerable while the
      // cluster cannot satisfy its own replication factor, e.g. with a broker down.
      //
      // The guard belongs to this entry point alone: a membership change does ask for something
      // even when neither partition dimension moves, and running the distributor is then exactly
      // right.
      return Either.right(List.of());
    }
    return phases(
        targetGroupId,
        clusterConfiguration,
        distributionByGroup,
        clusterConfiguration.liveMembers(),
        newPartitionCount,
        newReplicationFactor);
  }

  /**
   * As {@link #phases(String, CurrentClusterConfiguration, Optional, Optional)}, but places the
   * partitions on {@code targetMembers} rather than on whichever members are currently live, and
   * always runs the distributor.
   *
   * @param targetMembers the members every partition of every group should end up on — the complete
   *     desired member set, so a member being removed from the cluster is simply absent from it
   */
  static Either<Exception, List<Phase>> phases(
      final String targetGroupId,
      final CurrentClusterConfiguration clusterConfiguration,
      final Set<MemberId> targetMembers,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    return phases(
        targetGroupId,
        clusterConfiguration,
        ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(clusterConfiguration),
        targetMembers,
        newPartitionCount,
        newReplicationFactor);
  }

  /**
   * Takes the current per-tenant distribution rather than scanning it out of {@code
   * clusterConfiguration}: the entry point above needs it to decide whether the request asks for
   * anything at all, and passes on what it already scanned.
   */
  private static Either<Exception, List<Phase>> phases(
      final String targetGroupId,
      final CurrentClusterConfiguration clusterConfiguration,
      final Map<String, Set<PartitionMetadata>> distributionByGroup,
      final Set<MemberId> targetMembers,
      final Optional<Integer> newPartitionCount,
      final Optional<Integer> newReplicationFactor) {
    final var currentPartitionCount = currentPartitionCount(distributionByGroup, targetGroupId);
    final int targetPartitionCount = newPartitionCount.orElse(currentPartitionCount);

    final int replicationFactor =
        newReplicationFactor.orElseGet(() -> currentReplicationFactor(distributionByGroup));
    final var rejection =
        reject(
            targetGroupId,
            targetMembers,
            currentPartitionCount,
            targetPartitionCount,
            replicationFactor);
    if (rejection.isPresent()) {
      return Either.left(rejection.get());
    }

    final var newPartitionIds =
        IntStream.rangeClosed(currentPartitionCount + 1, targetPartitionCount)
            .mapToObj(number -> new PartitionId(targetGroupId, number))
            .toList();
    final var targetPartitionIds =
        Stream.concat(
                distributionByGroup.values().stream()
                    .flatMap(Set::stream)
                    .map(PartitionMetadata::id),
                newPartitionIds.stream())
            .toList();

    final Map<String, List<PartitionGroupOperation>> operationsByGroup;
    try {
      final var targetDistribution =
          targetDistribution(
              clusterConfiguration, targetMembers, targetPartitionIds, replicationFactor);
      operationsByGroup =
          new LinkedHashMap<>(
              PartitionReassignmentOperationsGenerator.generateOperations(
                  clusterConfiguration, targetDistribution, Map.of()));
    } catch (final RuntimeException e) {
      // What the distributor and the generator raise is a rejection of the request, not an internal
      // failure: a distributor that cannot place these partitions, a distribution that would drop
      // one.
      return Either.left(new InvalidRequest(e));
    }

    if (!newPartitionIds.isEmpty()) {
      final var newPartitionNumbers =
          new TreeSet<>(newPartitionIds.stream().map(PartitionId::number).toList());
      operationsByGroup.put(
          targetGroupId,
          withScaleUpOperations(
              operationsByGroup.getOrDefault(targetGroupId, List.of()),
              ClusterConfigurationCoordinatorSupplier.from(() -> clusterConfiguration)
                  .getDefaultCoordinator(),
              targetPartitionCount,
              newPartitionNumbers));
    }

    if (operationsByGroup.isEmpty()) {
      // Every partition already sits where the requested distribution puts it.
      return Either.right(List.of());
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(operationsByGroup)));
  }

  /**
   * The rejections that can be decided from the request and the current configuration alone, before
   * a distribution is computed. What the distributor itself rejects — a placement it cannot produce
   * for these members and zones — surfaces separately, as the {@code RuntimeException} it raises.
   *
   * <p>Both bounds on the replication factor are checked here rather than left to the distributor,
   * so that the operator sees why the request is impossible.
   *
   * @return the rejection to answer with, or empty if the request can be planned
   */
  private static Optional<Exception> reject(
      final String targetGroupId,
      final Set<MemberId> targetMembers,
      final int currentPartitionCount,
      final int targetPartitionCount,
      final int replicationFactor) {
    if (targetPartitionCount < currentPartitionCount) {
      return Optional.of(
          new InvalidRequest(
              "New partition count [%d] of physical tenant '%s' must be greater than or equal to its current partition count [%d]"
                  .formatted(targetPartitionCount, targetGroupId, currentPartitionCount)));
    }
    if (replicationFactor <= 0) {
      return Optional.of(
          new InvalidRequest(
              "Replication factor [%d] must be greater than 0".formatted(replicationFactor)));
    }
    if (targetMembers.size() < replicationFactor) {
      return Optional.of(
          new InvalidRequest(
              "Number of brokers [%d] is less than the replication factor [%d]"
                  .formatted(targetMembers.size(), replicationFactor)));
    }
    return Optional.empty();
  }

  private static int currentPartitionCount(
      final Map<String, Set<PartitionMetadata>> distributionByGroup, final String groupId) {
    return distributionByGroup.getOrDefault(groupId, Set.of()).size();
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
   * The replication factor every partition should keep when the request does not ask for one. Taken
   * as the minimum currently in use rather than a configured value, mirroring {@code
   * ClusterConfiguration#minReplicationFactor}: during a configuration change a partition can
   * temporarily hold more replicas than the cluster is configured for, and treating that as the
   * target would permanently widen it.
   */
  private static int currentReplicationFactor(
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
