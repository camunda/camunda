/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.*;
import io.camunda.webapps.backup.BackupService;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.profiles.ProfileWebApp;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.common.lang.NonNull;
import java.math.BigDecimal;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@WebEndpoint(id = "backupHistory")
@Conditional(WebappEnabledCondition.class)
@ProfileWebApp
public class BackupController {
  private static final Logger LOG = LoggerFactory.getLogger(BackupController.class);

  private final BackupService backupService;
  private final BackupRepositoryProps backupRepositoryProps;

  @Autowired
  public BackupController(
      final BackupService backupService, final BackupRepositoryProps backupProperties) {
    this.backupService = backupService;
    backupRepositoryProps = backupProperties;
  }

  @WriteOperation
  public WebEndpointResponse<?> takeBackup(final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var respDTO =
          backupService.takeBackup(new TakeBackupRequestDto().setBackupId(backupId));
      final var response =
          new TakeBackupHistoryResponse().scheduledSnapshots(respDTO.getScheduledSnapshots());
      return new WebEndpointResponse<>(response);
    } catch (final Exception e) {
      LOG.warn("Error when taking backup", e);
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackupState(@Selector @NonNull final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var respDTO = backupService.getBackupState(backupId);
      final var resp = mapTo(respDTO);
      return new WebEndpointResponse<>(resp);
    } catch (final Exception e) {
      LOG.warn("Error when trying to get a backup", e);
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackups(
      @RequestParam(value = "verbose", defaultValue = "true", required = false)
          final boolean verbose) {
    try {
      validateRepositoryNameIsConfigured();
      final var respDTO = backupService.getBackups(verbose);
      final var resp = respDTO.stream().map(this::mapTo).toList();
      return new WebEndpointResponse<>(resp);
    } catch (final Exception e) {
      LOG.warn("Error when trying to get all backup", e);
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
      LOG.warn("Error when trying to delete a backup", e);
      return mapErrorResponse(e);
    }
  }

  private void validateRepositoryNameIsConfigured() {
    if (backupRepositoryProps == null
        || backupRepositoryProps.repositoryName() == null
        || backupRepositoryProps.repositoryName().isEmpty()) {
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
  public static HistoryStateCode mapState(final BackupStateDto state) {
    return switch (state) {
      case IN_PROGRESS -> HistoryStateCode.IN_PROGRESS;
      case INCOMPLETE -> HistoryStateCode.INCOMPLETE;
      case COMPLETED -> HistoryStateCode.COMPLETED;
      case FAILED -> HistoryStateCode.FAILED;
      case INCOMPATIBLE -> HistoryStateCode.INCOMPATIBLE;
    };
  }

  private HistoryBackupDetail mapTo(final GetBackupStateResponseDetailDto detail) {
    return new HistoryBackupDetail(
        detail.getSnapshotName(),
        detail.getState(),
        detail.getStartTime(),
        detail.getFailures() != null ? Arrays.asList(detail.getFailures()) : null);
  }

  private HistoryBackupInfo mapTo(final GetBackupStateResponseDto detail) {
    final var info =
        new HistoryBackupInfo(
            new BigDecimal(detail.getBackupId()),
            mapState(detail.getState()),
            detail.getDetails().stream().map(this::mapTo).toList());
    if (detail.getFailureReason() != null) {
      info.setFailureReason(detail.getFailureReason());
    }

    return info;
  }

  private WebEndpointResponse<?> mapErrorResponse(final Exception exception) {
    final String message = exception.getMessage();
    int errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
    if (exception instanceof BackupException) {
      errorCode =
          switch (exception) {
            case final InvalidRequestException ignored -> WebEndpointResponse.STATUS_BAD_REQUEST;
            case final ResourceNotFoundException ignored -> WebEndpointResponse.STATUS_NOT_FOUND;
            case final MissingRepositoryException ignored -> WebEndpointResponse.STATUS_NOT_FOUND;
            case final BackupRepositoryConnectionException ignored -> 502;
            default -> WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
          };
    }
    return new WebEndpointResponse<>(new Error().message(message), errorCode);
  }
}
