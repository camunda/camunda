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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Removes a broker from one partition's replication group, in a single physical tenant's partition
 * group.
 */
public final class LeavePartitionRequestTransformer implements ConfigurationChangeRequest {

  /**
   * An operator-driven leave must not take a partition below one replica; only a cluster purge is
   * allowed to empty it.
   */
  private static final int MINIMUM_ALLOWED_REPLICAS = 1;

  private final MemberId memberId;
  private final int partitionId;
  private final Optional<String> physicalTenantId;

  public LeavePartitionRequestTransformer(
      final MemberId memberId, final int partitionId, final Optional<String> physicalTenantId) {
    this.memberId = memberId;
    this.partitionId = partitionId;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);
    if (!clusterConfiguration.hasPartitionGroup(groupId)) {
      return Either.left(
          new NotFound(
              "Expected to leave partition %d of physical tenant '%s', but the physical tenant does not exist"
                  .formatted(partitionId, groupId)));
    }

    // A two-phase leave: the member is demoted to a non-voting member first, so that the removal
    // commits without its participation. The demotion is only allowed when another active replica
    // remains - otherwise the leave is emitted alone and rejected by its applier, as before.
    final var group = clusterConfiguration.partitionGroup(groupId);
    final var otherActiveReplicaExists =
        group.members().entrySet().stream()
            .filter(entry -> !entry.getKey().equals(memberId))
            .map(entry -> entry.getValue().getPartition(partitionId))
            .anyMatch(
                partition -> partition != null && partition.state() == PartitionState.State.ACTIVE);
    final List<PartitionGroupOperation> operations =
        otherActiveReplicaExists
            ? List.of(
                new PartitionDemoteOperation(memberId, partitionId),
                new PartitionLeaveOperation(memberId, partitionId, MINIMUM_ALLOWED_REPLICAS))
            : List.of(new PartitionLeaveOperation(memberId, partitionId, MINIMUM_ALLOWED_REPLICAS));
    return Either.right(List.of(PartitionGroupPhase.sequential(Map.of(groupId, operations))));
  }
}
