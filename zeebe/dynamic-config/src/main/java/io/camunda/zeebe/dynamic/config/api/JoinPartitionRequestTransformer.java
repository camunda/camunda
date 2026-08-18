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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;

/**
 * Adds a broker to one partition's replication group, or changes the priority it already replicates
 * that partition with.
 */
public final class JoinPartitionRequestTransformer implements ConfigurationChangeRequest {

  private final MemberId memberId;
  private final int partitionId;
  private final int priority;

  public JoinPartitionRequestTransformer(
      final MemberId memberId, final int partitionId, final int priority) {
    this.memberId = memberId;
    this.partitionId = partitionId;
    this.priority = priority;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    throw new UnsupportedOperationException(
        "JoinPartitionRequestTransformer builds its change plan via "
            + "phases(CurrentClusterConfiguration); the new-model coordinator path never calls "
            + "operations(ClusterConfiguration)");
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final List<PartitionGroupOperation> operations =
        List.of(new PartitionJoinOperation(memberId, partitionId, priority));
    return Either.right(
        List.of(
            new PartitionGroupParallelPhase(
                Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, operations))));
  }
}
