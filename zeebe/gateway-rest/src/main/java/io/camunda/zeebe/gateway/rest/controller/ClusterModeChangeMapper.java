/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static java.util.Objects.requireNonNull;

import io.camunda.gateway.protocol.model.ClusterModeChangeOperation;
import io.camunda.gateway.protocol.model.ClusterModeChangePlannedChange;
import io.camunda.gateway.protocol.model.ClusterModeChangeResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupParallelPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Maps the cluster configuration change plan of a mode transition onto the REST response, shared by
 * the per-physical-tenant {@link RecoveryController} and the cluster-wide {@link
 * ClusterRecoveryController}.
 */
@NullMarked
final class ClusterModeChangeMapper {

  private ClusterModeChangeMapper() {}

  static <T> T unwrapOrThrow(final Either<ErrorResponse, T> result) {
    if (result.isRight()) {
      return result.get();
    }
    final var error = result.getLeft();
    throw new ServiceException(error.message(), mapErrorStatus(error.code()));
  }

  static ClusterModeChangeResponse toClusterModeChangeResponse(
      final ClusterConfigurationChangeResponse response) {
    return ClusterModeChangeResponse.Builder.create()
        .changeId(Long.toString(response.changeId()))
        .plannedChanges(toPlannedChanges(response))
        .build();
  }

  /**
   * Flattens the phases of the change plan into one entry per physical tenant, keyed by the
   * partition group the operations were planned for. Sorted by tenant so that a response never
   * depends on the iteration order of the plan's group map.
   */
  private static List<ClusterModeChangePlannedChange> toPlannedChanges(
      final ClusterConfigurationChangeResponse response) {
    final Map<String, List<ClusterModeChangeOperation>> operationsPerTenant = new TreeMap<>();
    final List<ClusterModeChangeOperation> clusterWideOperations = new ArrayList<>();
    for (final Phase phase : requireNonNull(response.response()).phases()) {
      switch (phase) {
        case final PartitionGroupParallelPhase parallelPhase ->
            parallelPhase
                .groupOperations()
                .forEach(
                    (physicalTenantId, operations) ->
                        operationsPerTenant
                            .computeIfAbsent(physicalTenantId, tenant -> new ArrayList<>())
                            .addAll(toClusterModeChangeOperations(operations)));
        // Broker lifecycle operations belong to the cluster, not to any single physical tenant.
        case final GlobalPhase globalPhase ->
            clusterWideOperations.addAll(toClusterModeChangeOperations(globalPhase.operations()));
      }
    }

    final var plannedChanges = new ArrayList<ClusterModeChangePlannedChange>();
    operationsPerTenant.forEach(
        (physicalTenantId, operations) ->
            plannedChanges.add(toPlannedChange(physicalTenantId, operations)));
    if (!clusterWideOperations.isEmpty()) {
      plannedChanges.add(toPlannedChange(null, clusterWideOperations));
    }
    return plannedChanges;
  }

  private static ClusterModeChangePlannedChange toPlannedChange(
      final @Nullable String physicalTenantId, final List<ClusterModeChangeOperation> operations) {
    return ClusterModeChangePlannedChange.Builder.create()
        .physicalTenantId(physicalTenantId)
        .operations(operations)
        .build();
  }

  private static List<ClusterModeChangeOperation> toClusterModeChangeOperations(
      final List<? extends ClusterConfigurationChangeOperation> operations) {
    return operations.stream().map(ClusterModeChangeMapper::toClusterModeChangeOperation).toList();
  }

  private static Status mapErrorStatus(final ErrorResponse.ErrorCode code) {
    return switch (code) {
      case INVALID_REQUEST -> Status.INVALID_ARGUMENT;
      case OPERATION_NOT_ALLOWED -> Status.FORBIDDEN;
      case CONCURRENT_MODIFICATION, INVALID_STATE -> Status.INVALID_STATE;
      case INTERNAL_ERROR -> Status.INTERNAL;
      case NOT_FOUND -> Status.NOT_FOUND;
    };
  }

  private static ClusterModeChangeOperation toClusterModeChangeOperation(
      final ClusterConfigurationChangeOperation operation) {
    final @Nullable String mode =
        switch (operation) {
          case final ModeChangeOperation modeChange -> modeChange.mode().name();
          case final AwaitModeChangeOperation awaitModeChange -> awaitModeChange.mode().name();
          default -> null;
        };
    return ClusterModeChangeOperation.Builder.create()
        .operation(operation.getClass().getSimpleName())
        .mode(mode)
        .build();
  }
}
