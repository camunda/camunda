/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.protocol.model.ClusterModeChangeOperation;
import io.camunda.gateway.protocol.model.ClusterModeChangeResponse;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.spring.utils.DatabaseTypeUtils;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping(path = "/v2")
public final class RecoveryController {

  private static final Logger LOG = LoggerFactory.getLogger(RecoveryController.class);
  private static final String CONTINUOUS_BACKUPS_PROPERTY =
      "camunda.data.primary-storage.backup.continuous";

  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender;
  private final Environment environment;

  public RecoveryController(
      final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender,
      final Environment environment) {
    this.clusterConfigurationRequestSender = clusterConfigurationRequestSender;
    this.environment = environment;
  }

  @CamundaPatchMapping(
      path = "/mode",
      consumes = {})
  public CompletableFuture<ResponseEntity<Object>> changeClusterMode(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam final Mode mode,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    LOG.debug("Requested cluster mode change to {} for physical tenant {}", mode, physicalTenantId);
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterConfigurationRequestSender
                .modeChange(new ModeChangeRequest(mode, dryRun))
                .thenApply(RecoveryController::unwrapOrThrow),
        RecoveryController::toClusterModeChangeResponse,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/restore")
  public CompletableFuture<ResponseEntity<Object>> restore(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false)
          final io.camunda.gateway.protocol.model.RestoreRequest restoreRequest,
      @RequestParam(name = "dryRun", defaultValue = "false") final boolean dryRun) {
    LOG.info("Requested restore for physical tenant {}: {}", physicalTenantId, restoreRequest);
    return RequestExecutor.executeServiceMethod(
        () ->
            clusterConfigurationRequestSender
                .restore(toRestoreRequest(physicalTenantId, restoreRequest, dryRun))
                .thenApply(RecoveryController::unwrapOrThrow),
        RecoveryController::toClusterModeChangeResponse,
        HttpStatus.ACCEPTED);
  }

  private RestoreRequest toRestoreRequest(
      final String physicalTenantId,
      final io.camunda.gateway.protocol.model.RestoreRequest restoreRequest,
      final boolean dryRun) {
    final String databaseType = DatabaseTypeUtils.getDatabaseTypeOrDefault(environment);
    final boolean continuousBackups =
        environment.getProperty(CONTINUOUS_BACKUPS_PROPERTY, Boolean.class, false);
    if (restoreRequest == null) {
      return new RestoreRequest(
          physicalTenantId, List.of(), null, null, databaseType, continuousBackups, dryRun);
    }
    final List<Long> backupIds =
        restoreRequest.getBackupIds() == null ? List.of() : restoreRequest.getBackupIds();
    return new RestoreRequest(
        physicalTenantId,
        backupIds,
        restoreRequest.getFrom(),
        restoreRequest.getTo(),
        databaseType,
        continuousBackups,
        dryRun);
  }

  private static ClusterConfigurationChangeResponse unwrapOrThrow(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> result) {
    if (result.isRight()) {
      return result.get();
    }
    final var error = result.getLeft();
    throw new ServiceException(error.message(), mapErrorStatus(error.code()));
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

  private static ClusterModeChangeResponse toClusterModeChangeResponse(
      final ClusterConfigurationChangeResponse response) {
    final List<ClusterModeChangeOperation> plannedChanges =
        response.plannedChanges().stream()
            .map(RecoveryController::toClusterModeChangeOperation)
            .toList();
    return ClusterModeChangeResponse.Builder.create()
        .changeId(Long.toString(response.changeId()))
        .plannedChanges(plannedChanges)
        .build();
  }

  private static ClusterModeChangeOperation toClusterModeChangeOperation(
      final ClusterConfigurationChangeOperation operation) {
    final String mode =
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
