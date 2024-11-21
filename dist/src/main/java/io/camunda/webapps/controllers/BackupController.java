/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import io.camunda.management.backups.Error;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.operate.exceptions.OperateElasticsearchConnectionException;
import io.camunda.operate.exceptions.OperateOpensearchConnectionException;
import io.camunda.operate.property.BackupProperties;
// FIXME this must be from webapps-backup when #24901 is merged
import io.camunda.operate.webapp.api.v1.exceptions.ResourceNotFoundException;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
// FIXME decide how to unify this
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.profiles.ProfileOperateStandalone;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.common.lang.NonNull;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backup-history")
@ProfileOperateStandalone
public class BackupController {

  private final BackupService backupService;
  // FIXME this should be BackupServiceProps see #24901
  private final BackupProperties backupProperties;

  public BackupController(
      final BackupService backupService, final BackupProperties backupProperties) {
    this.backupService = backupService;
    this.backupProperties = backupProperties;
  }

  @WriteOperation
  public WebEndpointResponse<?> takeBackup(final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var respDTO =
          backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
      return new WebEndpointResponse<>(
          new TakeBackupHistoryResponse().scheduledSnapshots(respDTO.getScheduledSnapshots()));
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackupState(@Selector @NonNull final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var respDTO = backupService.getBackupState(backupId);
      return new WebEndpointResponse<>(mapTo(respDTO));
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackups() {
    try {
      validateRepositoryNameIsConfigured();
      final var respDTO = backupService.getBackups();
      return new WebEndpointResponse<>(respDTO.stream().map(this::mapTo).toList());
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  @DeleteOperation
  public WebEndpointResponse<?> deleteBackup(@Selector @NonNull final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      backupService.deleteBackup(backupId);
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  private void validateRepositoryNameIsConfigured() {
    if (backupProperties == null
        || backupProperties.getRepositoryName() == null
        || backupProperties.getRepositoryName().isEmpty()) {
      throw new InvalidRequestException("No backup repository configured.");
    }
  }

  private void validateBackupId(final Long backupId) {
    if (backupId <= 0) {
      throw new InvalidRequestException(
          "BackupId must be a non-negative Integer. Received value: " + backupId);
    }
  }

  @VisibleForTesting
  public static HistoryStateCode mapTo(final BackupStateDto state) {
    return switch (state) {
      case IN_PROGRESS -> HistoryStateCode.IN_PROGRESS;
      case INCOMPLETE -> HistoryStateCode.INCOMPLETE;
      case COMPLETED -> HistoryStateCode.SUCCESS;
      case FAILED -> HistoryStateCode.FAILED;
      case INCOMPATIBLE -> HistoryStateCode.INCOMPATIBLE;
    };
  }

  private HistoryBackupDetail mapTo(final GetBackupStateResponseDetailDto detail) {
    // FIXME mapping from string to BackupStateDTO is not expected to be 1:1
    return new HistoryBackupDetail(
        detail.getSnapshotName(),
        mapTo(BackupStateDto.valueOf(detail.getState())),
        detail.getStartTime(),
        detail.getFailures() != null ? Arrays.asList(detail.getFailures()) : List.of());
  }

  private HistoryBackupInfo mapTo(final GetBackupStateResponseDto detail) {
    return new HistoryBackupInfo(
        new BigDecimal(detail.getBackupId()),
        mapTo(detail.getState()),
        detail.getDetails().stream().map(this::mapTo).toList());
  }

  private WebEndpointResponse<?> mapErrorResponse(final Exception exception) {
    final String message = exception.getMessage();
    int errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
    switch (exception) {
      case final InvalidRequestException ignored -> {
        errorCode = WebEndpointResponse.STATUS_BAD_REQUEST;
      }
      case final ResourceNotFoundException ignored ->
          errorCode = WebEndpointResponse.STATUS_NOT_FOUND;
      // FIXME
      case final OperateElasticsearchConnectionException ignored -> errorCode = 502;
      case final OperateOpensearchConnectionException ignored -> errorCode = 502;
      default -> {}
    }
    return new WebEndpointResponse<>(new Error().message(message), errorCode);
  }
}
