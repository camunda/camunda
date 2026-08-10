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
import io.camunda.gateway.protocol.model.ClusterModeChangeResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.util.Either;
import java.util.List;
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

  private static List<ClusterModeChangeOperation> toPlannedChanges(
      final ClusterConfigurationChangeResponse response) {
    return requireNonNull(response.response()).plannedChanges().stream()
        .map(ClusterModeChangeMapper::toClusterModeChangeOperation)
        .toList();
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
