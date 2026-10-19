/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestFailedException.NotFound;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinator.ConfigurationChangeRequest;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Changes the exporting state, either for a single physical tenant or, when none is given, for
 * every physical tenant of the cluster.
 */
public final class ExportingStateChangeRequestTransformer implements ConfigurationChangeRequest {

  private final ExportingState targetState;
  private final Optional<String> physicalTenantId;

  public ExportingStateChangeRequestTransformer(final ExportingState targetState) {
    this(targetState, Optional.empty());
  }

  public ExportingStateChangeRequestTransformer(
      final ExportingState targetState, final Optional<String> physicalTenantId) {
    this.targetState = targetState;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to change the exporting state of physical tenant '%s', but there's no such tenant"
                  .formatted(physicalTenantId.get())));
    }

    final Map<String, List<PartitionGroupOperation>> operationsPerGroup = new LinkedHashMap<>();
    clusterConfiguration.partitionGroups().entrySet().stream()
        .filter(
            group -> physicalTenantId.isEmpty() || physicalTenantId.get().equals(group.getKey()))
        .forEach(
            group -> {
              final var operations = exportingStateChangeOperations(group.getValue());
              if (!operations.isEmpty()) {
                operationsPerGroup.put(group.getKey(), operations);
              }
            });

    if (operationsPerGroup.isEmpty()) {
      return Either.right(List.of());
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(operationsPerGroup)));
  }

  private List<PartitionGroupOperation> exportingStateChangeOperations(
      final PartitionGroupConfiguration group) {
    return group.members().entrySet().stream()
        .filter(e -> needsChange(e.getValue()))
        .map(
            e ->
                (PartitionGroupOperation)
                    new ExportingStateChangeOperation(e.getKey(), targetState))
        .toList();
  }

  private boolean needsChange(final BrokerPartitionState brokerPartitionState) {
    return brokerPartitionState.partitions().values().stream().anyMatch(this::notInTargetState);
  }

  private boolean notInTargetState(final PartitionState partitionState) {
    final var config = partitionState.config();
    return !config.isInitialized() || config.exporting().state() != targetState;
  }
}
