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
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Enables an exporter, either for a single physical tenant or, when none is given, for every
 * physical tenant. Unlike disable and delete, an exporter does not need to already exist on a
 * partition to be enabled there.
 */
final class ExporterEnableRequestTransformer implements ConfigurationChangeRequest {

  private final String exporterId;
  private final Optional<String> initializeFrom;
  private final Optional<String> physicalTenantId;

  public ExporterEnableRequestTransformer(
      final String exporterId, final Optional<String> initializeFrom) {
    this(exporterId, initializeFrom, Optional.empty());
  }

  public ExporterEnableRequestTransformer(
      final String exporterId,
      final Optional<String> initializeFrom,
      final Optional<String> physicalTenantId) {
    this.exporterId = exporterId;
    this.initializeFrom = initializeFrom;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to enable exporter '%s' for physical tenant '%s', but the physical tenant does not exist"
                  .formatted(exporterId, physicalTenantId.get())));
    }

    final List<String> groupIds =
        physicalTenantId
            .map(List::of)
            .orElseGet(() -> List.copyOf(clusterConfiguration.partitionGroups().keySet()));

    final Map<String, List<PartitionGroupOperation>> groupOperations = new LinkedHashMap<>();
    for (final var groupId : groupIds) {
      final var operations =
          enableOperationsFor(Objects.requireNonNull(clusterConfiguration.partitionGroup(groupId)));
      if (!operations.isEmpty()) {
        groupOperations.put(groupId, operations);
      }
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(groupOperations)));
  }

  private List<PartitionGroupOperation> enableOperationsFor(
      final PartitionGroupConfiguration group) {
    final List<PartitionGroupOperation> operations = new ArrayList<>();
    for (final var member : group.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partition : member.getValue().partitions().entrySet()) {
        final var partitionId = partition.getKey();
        operations.add(
            new PartitionEnableExporterOperation(
                memberId, partitionId, exporterId, initializeFrom));
      }
    }
    return operations;
  }
}
