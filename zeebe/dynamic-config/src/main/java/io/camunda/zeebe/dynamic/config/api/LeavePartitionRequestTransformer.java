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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;

/** Removes a broker from one partition's replication group. */
public final class LeavePartitionRequestTransformer implements ConfigurationChangeRequest {

  /**
   * An operator-driven leave must not take a partition below one replica; only a cluster purge is
   * allowed to empty it.
   */
  private static final int MINIMUM_ALLOWED_REPLICAS = 1;

  private final MemberId memberId;
  private final int partitionId;

  public LeavePartitionRequestTransformer(final MemberId memberId, final int partitionId) {
    this.memberId = memberId;
    this.partitionId = partitionId;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    throw new UnsupportedOperationException(
        "LeavePartitionRequestTransformer builds its change plan via "
            + "phases(CurrentClusterConfiguration); the new-model coordinator path never calls "
            + "operations(ClusterConfiguration)");
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    final List<PartitionGroupOperation> operations =
        List.of(new PartitionLeaveOperation(memberId, partitionId, MINIMUM_ALLOWED_REPLICAS));
    return Either.right(
        List.of(
            new PartitionGroupParallelPhase(
                Map.of(CurrentClusterConfiguration.DEFAULT_GROUP, operations))));
  }
}
