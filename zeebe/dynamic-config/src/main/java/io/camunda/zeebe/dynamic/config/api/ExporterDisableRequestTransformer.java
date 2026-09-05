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
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
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
 * Disables an exporter, either for a single physical tenant or, when none is given, for every
 * physical tenant that currently has it configured. Exporter configuration is per physical tenant,
 * so an exporter id may not exist in every tenant; tenants (and partitions within a tenant) that do
 * not have it configured are silently skipped rather than failing the whole request.
 */
public final class ExporterDisableRequestTransformer implements ConfigurationChangeRequest {

  private final String exporterId;
  private final Optional<String> physicalTenantId;

  public ExporterDisableRequestTransformer(final String exporterId) {
    this(exporterId, Optional.empty());
  }

  public ExporterDisableRequestTransformer(
      final String exporterId, final Optional<String> physicalTenantId) {
    this.exporterId = exporterId;
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public Either<Exception, List<Phase>> phases(
      final CurrentClusterConfiguration clusterConfiguration) {
    if (physicalTenantId.isPresent()
        && !clusterConfiguration.hasPartitionGroup(physicalTenantId.get())) {
      return Either.left(
          new NotFound(
              "Expected to disable exporter '%s' for physical tenant '%s', but the physical tenant does not exist"
                  .formatted(exporterId, physicalTenantId.get())));
    }

    final List<String> groupIds =
        physicalTenantId
            .<List<String>>map(List::of)
            .orElseGet(() -> List.copyOf(clusterConfiguration.partitionGroups().keySet()));

    final Map<String, List<PartitionGroupOperation>> groupOperations = new LinkedHashMap<>();
    for (final var groupId : groupIds) {
      final var operations =
          disableOperationsFor(
              Objects.requireNonNull(clusterConfiguration.partitionGroup(groupId)));
      if (!operations.isEmpty()) {
        groupOperations.put(groupId, operations);
      }
    }

    if (groupOperations.isEmpty()) {
      return Either.left(
          new NotFound(
              "Expected to disable exporter '%s' for physical tenant '%s', but no matching exporters were found"
                  .formatted(exporterId, physicalTenantId.orElse("all tenants"))));
    }
    return Either.right(List.of(PartitionGroupPhase.sequential(groupOperations)));
  }

  private List<PartitionGroupOperation> disableOperationsFor(
      final PartitionGroupConfiguration group) {
    final List<PartitionGroupOperation> operations = new ArrayList<>();
    for (final var member : group.members().entrySet()) {
      final var memberId = member.getKey();
      for (final var partition : member.getValue().partitions().entrySet()) {
        final var partitionId = partition.getKey();
        final var hasExporter =
            partition.getValue().config().exporting().exporters().containsKey(exporterId);
        if (hasExporter) {
          operations.add(new PartitionDisableExporterOperation(memberId, partitionId, exporterId));
        }
      }
    }
    return operations;
  }
}
