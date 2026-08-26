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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adds a broker to one partition's replication group, or changes the priority it already replicates
 * that partition with.
 */
public final class JoinPartitionRequestTransformer implements ConfigurationChangeRequest {

  private final MemberId memberId;
  private final int partitionId;
  private final int priority;
  private final Optional<String> physicalTenantId;

  public JoinPartitionRequestTransformer(
      final MemberId memberId,
      final int partitionId,
      final int priority,
      final Optional<String> physicalTenantId) {
    this.memberId = memberId;
    this.partitionId = partitionId;
    this.priority = priority;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final var groupId = physicalTenantId.orElse(CurrentClusterConfiguration.DEFAULT_GROUP);
    if (!clusterConfiguration.hasPartitionGroup(groupId)) {
      return Either.left(
          new NotFound(
              "Expected to join partition %d of physical tenant '%s', but the physical tenant does not exist"
                  .formatted(partitionId, groupId)));
    }

    // A two-phase join: the member joins as a learner and is promoted to a voting member once it
    // has caught up on the partition's log.
    final List<PartitionGroupOperation> operations =
        List.of(
            new PartitionJoinOperation(memberId, partitionId, priority),
            new PartitionPromoteOperation(memberId, partitionId));
    return Either.right(List.of(PartitionGroupPhase.sequential(Map.of(groupId, operations))));
  }
}
