/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.rebalance;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/** Decides what a rebalance will do to each partition. */
public final class PartitionBalancePlanner {

  private final PartitionLeaders partitionLeaders;

  public PartitionBalancePlanner(final PartitionLeaders partitionLeaders) {
    this.partitionLeaders = partitionLeaders;
  }

  public List<PartitionRebalance> plan(final CurrentClusterConfiguration configuration) {
    return configuration.activePartitionGroups().entrySet().stream()
        .filter(entry -> !entry.getValue().isRecovering())
        .sorted(Map.Entry.comparingByKey())
        .flatMap(entry -> planGroup(entry.getKey(), entry.getValue()))
        .toList();
  }

  private Stream<PartitionRebalance> planGroup(
      final String physicalTenantId, final PartitionGroupConfiguration group) {
    final var groupLeaders = partitionLeaders.forGroup(physicalTenantId);
    return group
        .partitionIds()
        .mapToObj(partitionId -> planPartition(physicalTenantId, group, groupLeaders, partitionId));
  }

  /**
   * {@code transferring} is the single partition a running rebalance currently has in flight, if
   * any (it will be marked {@link PartitionLeadershipStatus.State#TRANSFERRING} in the returned
   * status).
   */
  public ClusterLeadershipStatus leadershipStatus(
      final CurrentClusterConfiguration configuration,
      final @Nullable PartitionRebalance currentTransfer) {
    final var partitions =
        plan(configuration).stream()
            .map(partition -> toLeadershipStatus(partition, currentTransfer))
            .toList();
    return ClusterLeadershipStatus.aggregateOf(partitions);
  }

  private PartitionLeadershipStatus toLeadershipStatus(
      final PartitionRebalance partition, final @Nullable PartitionRebalance currentTransfer) {
    final var state =
        isSamePartition(partition, currentTransfer)
            ? PartitionLeadershipStatus.State.TRANSFERRING
            : partition.isBalanced()
                ? PartitionLeadershipStatus.State.BALANCED
                : PartitionLeadershipStatus.State.UNBALANCED;
    return new PartitionLeadershipStatus(
        partition.physicalTenantId(),
        partition.partitionId(),
        partition.currentLeader(),
        partition.desiredLeader(),
        state);
  }

  private boolean isSamePartition(
      final PartitionRebalance left, final @Nullable PartitionRebalance right) {
    return right != null
        && left.physicalTenantId().equals(right.physicalTenantId())
        && left.partitionId() == right.partitionId();
  }

  private PartitionRebalance planPartition(
      final String physicalTenantId,
      final PartitionGroupConfiguration group,
      final PartitionLeaders.PartitionGroupLeaders groupLeaders,
      final int partitionId) {
    final var desiredLeader =
        group
            .getDesiredLeader(partitionId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No member of physical tenant %s's partition group is eligible to lead "
                            + "partition %d, so it cannot be planned"
                                .formatted(physicalTenantId, partitionId)));
    final var currentLeader = groupLeaders.currentLeader(partitionId);
    if (currentLeader.map(desiredLeader::equals).orElse(false)) {
      return PartitionRebalance.alreadyLeader(physicalTenantId, partitionId, desiredLeader);
    }
    return PartitionRebalance.pending(
        physicalTenantId, partitionId, currentLeader.orElse(null), desiredLeader);
  }
}
