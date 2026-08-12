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
import io.camunda.gateway.protocol.model.ClusterRestoreOperation;
import io.camunda.gateway.protocol.model.ClusterRestorePlannedChange;
import io.camunda.gateway.protocol.model.ClusterRestoreResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
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
 * Maps the cluster configuration change plan of a mode transition or a restore onto the REST
 * response, shared by the per-physical-tenant {@link RecoveryController} and the cluster-wide
 * {@link ClusterRecoveryController}.
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
        .plannedChanges(
            groupByPhysicalTenant(response).stream()
                .map(
                    group ->
                        ClusterModeChangePlannedChange.Builder.create()
                            .physicalTenantId(group.physicalTenantId())
                            .operations(
                                group.operations().stream()
                                    .map(ClusterModeChangeMapper::toClusterModeChangeOperation)
                                    .toList())
                            .build())
                .toList())
        .build();
  }

  static ClusterRestoreResponse toClusterRestoreResponse(
      final ClusterConfigurationChangeResponse response) {
    return ClusterRestoreResponse.Builder.create()
        .changeId(Long.toString(response.changeId()))
        .plannedChanges(
            groupByPhysicalTenant(response).stream()
                .map(
                    group ->
                        ClusterRestorePlannedChange.Builder.create()
                            .physicalTenantId(group.physicalTenantId())
                            .operations(
                                group.operations().stream()
                                    .map(ClusterModeChangeMapper::toClusterRestoreOperation)
                                    .toList())
                            .build())
                .toList())
        .build();
  }

  /**
   * Flattens the phases of the change plan into one group per physical tenant, keyed by the
   * partition group the operations were planned for. Sorted by tenant so that a response never
   * depends on the iteration order of the plan's group map. Operations that are not scoped to a
   * single physical tenant land in a trailing group with a null tenant.
   */
  private static List<PlannedGroup> groupByPhysicalTenant(
      final ClusterConfigurationChangeResponse response) {
    final Map<String, List<ClusterConfigurationChangeOperation>> operationsPerTenant =
        new TreeMap<>();
    final List<ClusterConfigurationChangeOperation> clusterWideOperations = new ArrayList<>();
    for (final Phase phase : requireNonNull(response.response()).phases()) {
      switch (phase) {
        case final PartitionGroupParallelPhase parallelPhase ->
            parallelPhase
                .groupOperations()
                .forEach(
                    (physicalTenantId, operations) ->
                        operationsPerTenant
                            .computeIfAbsent(physicalTenantId, tenant -> new ArrayList<>())
                            .addAll(operations));
        // Broker lifecycle operations belong to the cluster, not to any single physical tenant.
        case final GlobalPhase globalPhase ->
            clusterWideOperations.addAll(globalPhase.operations());
      }
    }

    final var groups = new ArrayList<PlannedGroup>();
    operationsPerTenant.forEach(
        (physicalTenantId, operations) ->
            groups.add(new PlannedGroup(physicalTenantId, operations)));
    if (!clusterWideOperations.isEmpty()) {
      groups.add(new PlannedGroup(null, clusterWideOperations));
    }
    return groups;
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
    return ClusterModeChangeOperation.Builder.create()
        .operation(operation.getClass().getSimpleName())
        .mode(modeOf(operation))
        .build();
  }

  /**
   * Reports the operation with the detail a restore plan is reviewed by: which broker applies it,
   * which partition it targets, and which backups that partition is restored from. Properties that
   * the operation does not carry are left null.
   */
  private static ClusterRestoreOperation toClusterRestoreOperation(
      final ClusterConfigurationChangeOperation operation) {
    final @Nullable Integer partitionId =
        operation instanceof final PartitionChangeOperation partitionChange
            ? partitionChange.partitionId()
            : null;
    final @Nullable List<Long> backupIds =
        operation instanceof final PartitionRestoreOperation restore
            ? List.copyOf(restore.backupIds())
            : null;
    return ClusterRestoreOperation.Builder.create()
        .operation(operation.getClass().getSimpleName())
        .brokerId(operation.brokerId())
        .partitionId(partitionId)
        .backupIds(backupIds)
        .mode(modeOf(operation))
        .build();
  }

  private static @Nullable String modeOf(final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final ModeChangeOperation modeChange -> modeChange.mode().name();
      case final AwaitModeChangeOperation awaitModeChange -> awaitModeChange.mode().name();
      default -> null;
    };
  }

  /** The operations of a change plan that apply to one physical tenant, in the planned order. */
  private record PlannedGroup(
      @Nullable String physicalTenantId, List<ClusterConfigurationChangeOperation> operations) {}
}
