/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class ExporterStateChangeRequestTransformer implements ConfigurationChangeRequest {

  private final ExportingState targetState;

  public ExporterStateChangeRequestTransformer(final ExportingState targetState) {
    this.targetState = targetState;
  }

  @Override
  public Either<Exception, List<ClusterConfigurationChangeOperation>> operations(
      final ClusterConfiguration clusterConfiguration) {
    // One operation per partition-owning member that is not already fully in the target state.
    // A member already converged (all its partitions in the target state) is skipped so that
    // re-sending the request is an idempotent no-op that yields an empty plan.
    final List<ClusterConfigurationChangeOperation> operations =
        clusterConfiguration.members().entrySet().stream()
            .filter(e -> needsChange(e.getValue()))
            .map(
                e ->
                    (ClusterConfigurationChangeOperation)
                        new ExportingStateChangeOperation(e.getKey(), targetState))
            .toList();
    return Either.right(operations);
  }

  private boolean needsChange(final MemberState memberState) {
    return memberState.partitions().values().stream().anyMatch(this::notInTargetState);
  }

  private boolean notInTargetState(final PartitionState partitionState) {
    final var config = partitionState.config();
    return !config.isInitialized() || config.exporting().state() != targetState;
  }
}
