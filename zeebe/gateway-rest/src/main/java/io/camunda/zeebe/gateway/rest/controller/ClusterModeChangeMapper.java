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
import io.camunda.gateway.protocol.model.ClusterRestoreAwaitModeChangeOperation;
import io.camunda.gateway.protocol.model.ClusterRestoreBrokerOperation;
import io.camunda.gateway.protocol.model.ClusterRestoreModeChangeOperation;
import io.camunda.gateway.protocol.model.ClusterRestoreOperation;
import io.camunda.gateway.protocol.model.ClusterRestorePartitionOperation;
import io.camunda.gateway.protocol.model.ClusterRestorePartitionRestoreOperation;
import io.camunda.gateway.protocol.model.ClusterRestorePlannedChange;
import io.camunda.gateway.protocol.model.ClusterRestoreResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
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
        // A phase carries its operations plus the edges between them. The edges say what may run
        // when, which this view does not report, so it reads the operations alone.
        case final PartitionGroupPhase groupPhase ->
            collectPerTenant(groupPhase.groupOperations(), operationsPerTenant);
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

  private static void collectPerTenant(
      final Map<String, ? extends List<? extends ClusterConfigurationChangeOperation>>
          groupOperations,
      final Map<String, List<ClusterConfigurationChangeOperation>> operationsPerTenant) {
    groupOperations.forEach(
        (physicalTenantId, operations) ->
            operationsPerTenant
                .computeIfAbsent(physicalTenantId, tenant -> new ArrayList<>())
                .addAll(operations));
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
   * plus what the operation itself carries — the partition it targets, the backups that partition
   * is restored from, or the mode the broker is transitioned to. Each kind is reported as its own
   * variant, so a property the operation does not carry is absent rather than null.
   */
  private static ClusterRestoreOperation toClusterRestoreOperation(
      final ClusterConfigurationChangeOperation operation) {
    final var type = operation.getClass().getSimpleName();
    return switch (operation) {
      case final PartitionRestoreOperation restore ->
          ClusterRestorePartitionRestoreOperation.Builder.create()
              .operation(type)
              .brokerId(restore.brokerId())
              .partitionId(restore.partitionId())
              .backupIds(List.copyOf(restore.backupIds()))
              .build();
      case final PartitionPreRestoreOperation preRestore ->
          ClusterRestorePartitionOperation.Builder.create()
              .operation(type)
              .brokerId(preRestore.brokerId())
              .partitionId(preRestore.partitionId())
              .build();
      case final ModeChangeOperation modeChange ->
          ClusterRestoreModeChangeOperation.Builder.create()
              .operation(type)
              .brokerId(modeChange.brokerId())
              .mode(modeChange.mode().name())
              .build();
      case final AwaitModeChangeOperation awaitModeChange ->
          ClusterRestoreAwaitModeChangeOperation.Builder.create()
              .operation(type)
              .brokerId(awaitModeChange.brokerId())
              .mode(awaitModeChange.mode().name())
              .build();
      default ->
          ClusterRestoreBrokerOperation.Builder.create()
              .operation(type)
              .brokerId(operation.brokerId())
              .build();
    };
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
