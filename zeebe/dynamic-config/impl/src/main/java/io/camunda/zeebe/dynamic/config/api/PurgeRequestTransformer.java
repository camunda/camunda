/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.DeleteHistoryOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Purges a cluster: every broker leaves its partitions, the exported history is deleted, and the
 * partitions are bootstrapped again with empty state, restoring the original topology.
 *
 * <p>Purging is per physical tenant: each tenant has its own partitions and its own exporters, so
 * purging one tenant must not touch another's data. When a physical tenant is given, only that
 * tenant's partition group is purged; without one, every tenant is purged, each within its own
 * group and in parallel with the others.
 */
public final class PurgeRequestTransformer implements ConfigurationChangeRequest {

  private final Optional<String> physicalTenantId;

  public PurgeRequestTransformer() {
    this(Optional.empty());
  }

  public PurgeRequestTransformer(final Optional<String> physicalTenantId) {
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to purge physical tenant '%s', but the physical tenant does not exist"
                  .formatted(physicalTenantId.get())));
    }

    final List<String> groupIds =
        physicalTenantId
            .<List<String>>map(List::of)
            .orElseGet(() -> List.copyOf(clusterConfiguration.partitionGroups().keySet()));

    final Map<String, List<PartitionGroupOperation>> groupOperations = new LinkedHashMap<>();
    for (final var groupId : groupIds) {
      final var operations =
          purgeOperationsFor(Objects.requireNonNull(clusterConfiguration.partitionGroup(groupId)));
      if (!operations.isEmpty()) {
        groupOperations.put(groupId, operations);
      }
    }

    if (groupOperations.isEmpty()) {
      return Either.right(List.of());
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(groupOperations)));
  }

  /**
   * Builds the purge plan of a single partition group: all members leave their partitions, the
   * history exported by this group is deleted, the group's incarnation number is bumped, and the
   * partitions are bootstrapped and joined again with their original priorities and configuration.
   *
   * <p>The history deletion and the incarnation bump are assigned to the lowest-id member that
   * actually replicates a partition of this group. A member can be present with an empty partition
   * map — transiently, while it is being removed from the group — and such a member is not active
   * in the group at all (see {@link PartitionGroupConfiguration}), so it holds none of the group's
   * exporter state and must not be the one to purge it. A group no member replicates has nothing to
   * tear down or re-bootstrap and yields no operations.
   */
  private List<PartitionGroupOperation> purgeOperationsFor(
      final PartitionGroupConfiguration group) {
    final var replicatingMember =
        group.members().entrySet().stream()
            .filter(member -> !member.getValue().partitions().isEmpty())
            .map(Entry::getKey)
            .findFirst();
    if (replicatingMember.isEmpty()) {
      return List.of();
    }

    final SortedMap<Integer, PartitionBootstrapOperation> primaries =
        createBootstrapOperations(group.members());

    final Map<Integer, List<PartitionGroupOperation>> followers =
        new TreeMap<>(Comparator.naturalOrder());

    // Every leave except a partition's last is preceded by a demotion, so the removal commits
    // without the departing member. The last replica must not be demoted - a non-empty replication
    // group without a voting member could neither elect a leader nor commit - and keeps the
    // one-shot leave, which as the only remaining member it can drive to the empty configuration.
    final Map<Integer, Long> remainingActiveReplicas = new TreeMap<>();
    for (final var member : group.members().values()) {
      member
          .partitions()
          .forEach(
              (partitionId, partition) -> {
                if (partition.state() == PartitionState.State.ACTIVE) {
                  remainingActiveReplicas.merge(partitionId, 1L, Long::sum);
                }
              });
    }

    final List<PartitionGroupOperation> operations = new ArrayList<>();
    for (final var member : group.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partitions : member.getValue().partitions().entrySet()) {
        final var partitionId = partitions.getKey();
        final var isActive = partitions.getValue().state() == PartitionState.State.ACTIVE;
        final var otherActiveReplicas =
            remainingActiveReplicas.getOrDefault(partitionId, 0L) - (isActive ? 1 : 0);
        if (otherActiveReplicas > 0) {
          operations.add(new PartitionDemoteOperation(memberId, partitionId));
        }
        operations.add(new PartitionLeaveOperation(memberId, partitionId, 0));
        if (isActive) {
          remainingActiveReplicas.merge(partitionId, -1L, Long::sum);
        }

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

    operations.add(new DeleteHistoryOperation(replicatingMember.get()));
    operations.add(new UpdateIncarnationNumberOperation(replicatingMember.get()));

    operations.addAll(primaries.values());
    followers.values().forEach(operations::addAll);

    return operations;
  }

  /** This method creates the BootstrapOperations for all leaders for each partition. */
  private SortedMap<Integer, PartitionBootstrapOperation> createBootstrapOperations(
      final Map<MemberId, BrokerPartitionState> members) {

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
