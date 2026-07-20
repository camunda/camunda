/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.controllers;

import static io.camunda.configuration.SecondaryStorage.SecondaryStorageType.elasticsearch;
import static io.camunda.configuration.SecondaryStorage.SecondaryStorageType.opensearch;

import io.camunda.application.commons.backup.BackupServiceRegistry;
import io.camunda.application.commons.backup.BackupServiceRegistry.PhysicalTenantBackup;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.HistoryBackupDetail;
import io.camunda.management.backups.HistoryBackupInfo;
import io.camunda.management.backups.HistoryBackupTenantInfo;
import io.camunda.management.backups.HistoryStateCode;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.webapps.backup.BackupException;
import io.camunda.webapps.backup.BackupException.*;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.webapps.backup.GetBackupStateResponseDetailDto;
import io.camunda.webapps.backup.GetBackupStateResponseDto;
import io.camunda.webapps.backup.TakeBackupRequestDto;
import io.camunda.zeebe.util.VisibleForTesting;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backupHistory")
@ConditionalOnSecondaryStorageType({elasticsearch, opensearch})
public class BackupController {
  private static final Logger LOG = LoggerFactory.getLogger(BackupController.class);

  private static final List<BackupStateDto> STATE_PRECEDENCE =
      List.of(
          BackupStateDto.FAILED,
          BackupStateDto.INCOMPATIBLE,
          BackupStateDto.INCOMPLETE,
          BackupStateDto.IN_PROGRESS,
          BackupStateDto.COMPLETED);

  private final BackupServiceRegistry backupServiceRegistry;

  public BackupController(final BackupServiceRegistry backupServiceRegistry) {
    this.backupServiceRegistry = backupServiceRegistry;
  }

  @WriteOperation
  public WebEndpointResponse<?> takeBackup(final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var scheduledSnapshots = new ArrayList<String>();
      for (final PhysicalTenantBackup backup : backupServiceRegistry.physicalTenantBackups()) {
        scheduledSnapshots.addAll(
            backup
                .backupService()
                .takeBackup(new TakeBackupRequestDto().setBackupId(backupId))
                .getScheduledSnapshots());
      }
      final var response = new TakeBackupHistoryResponse().scheduledSnapshots(scheduledSnapshots);
      return new WebEndpointResponse<>(response);
    } catch (final Exception e) {
      LOG.warn("Error when taking backup", e);
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackupState(@Selector final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      final var perPhysicalTenantStates =
          backupServiceRegistry.physicalTenantBackups().stream()
              .map(
                  backup ->
                      new TenantBackupState(
                          backup.physicalTenantId(),
                          backup.backupService().getBackupState(backupId)))
              .toList();
      final var resp = mapTo(aggregate(perPhysicalTenantStates));
      return new WebEndpointResponse<>(resp);
    } catch (final Exception e) {
      LOG.warn("Error when trying to get a backup", e);
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> getBackups(
      @Nullable final Boolean verbose, @Nullable final String pattern) {
    try {
      final boolean verboseValue = verbose != null ? verbose : true;
      final String patternValue = pattern != null ? pattern : "*";
      validateRepositoryNameIsConfigured();
      final var byBackupId =
          backupServiceRegistry.physicalTenantBackups().stream()
              .flatMap(
                  backup ->
                      backup.backupService().getBackups(verboseValue, patternValue).stream()
                          .map(dto -> new TenantBackupState(backup.physicalTenantId(), dto)))
              .collect(
                  Collectors.groupingBy(
                      state -> state.dto().getBackupId(), LinkedHashMap::new, Collectors.toList()));
      final var resp = byBackupId.values().stream().map(this::aggregate).map(this::mapTo).toList();
      return new WebEndpointResponse<>(resp);
    } catch (final Exception e) {
      LOG.warn("Error when trying to get all backup", e);
      return mapErrorResponse(e);
    }
  }

  @DeleteOperation
  public WebEndpointResponse<?> deleteBackup(@Selector final long backupId) {
    try {
      validateBackupId(backupId);
      validateRepositoryNameIsConfigured();
      backupServiceRegistry
          .physicalTenantBackups()
          .forEach(backup -> backup.backupService().deleteBackup(backupId));
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final Exception e) {
      LOG.warn("Error when trying to delete a backup", e);
      return mapErrorResponse(e);
    }
  }

  private void validateRepositoryNameIsConfigured() {
    if (backupServiceRegistry.isEmpty()) {
      throw new InvalidRequestException("No backup repository configured.");
    }
    final var tenantsMissingRepository =
        backupServiceRegistry.physicalTenantBackups().stream()
            .filter(
                backup ->
                    backup.repositoryProps().repositoryName() == null
                        || backup.repositoryProps().repositoryName().isEmpty())
            .map(PhysicalTenantBackup::physicalTenantId)
            .toList();
    if (!tenantsMissingRepository.isEmpty()) {
      throw new InvalidRequestException(
          "No backup repository configured for physical tenant(s): " + tenantsMissingRepository);
    }
  }

  private AggregatedBackupState aggregate(final List<TenantBackupState> perTenantStates) {
    final var states = perTenantStates.stream().map(TenantBackupState::dto).toList();
    final var aggregated = new GetBackupStateResponseDto(states.getFirst().getBackupId());
    aggregated.setState(worstStateOf(states));
    aggregated.setDetails(
        states.stream()
            .flatMap(dto -> dto.getDetails() == null ? Stream.empty() : dto.getDetails().stream())
            .toList());
    final var failureReasons =
        states.stream()
            .map(GetBackupStateResponseDto::getFailureReason)
            .filter(reason -> reason != null && !reason.isEmpty())
            .toList();
    if (!failureReasons.isEmpty()) {
      aggregated.setFailureReason(String.join("; ", failureReasons));
    }
    return new AggregatedBackupState(aggregated, perTenantStates);
  }

  private BackupStateDto worstStateOf(final List<GetBackupStateResponseDto> states) {
    final var statesPresent =
        states.stream().map(GetBackupStateResponseDto::getState).collect(Collectors.toSet());
    for (final BackupStateDto candidate : STATE_PRECEDENCE) {
      if (statesPresent.contains(candidate)) {
        return candidate;
      }
    }
    return BackupStateDto.COMPLETED;
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

  private HistoryBackupInfo mapTo(final AggregatedBackupState aggregated) {
    final var detail = aggregated.overall();
    final var info =
        new HistoryBackupInfo(
            new BigDecimal(detail.getBackupId()),
            mapState(detail.getState()),
            detail.getDetails().stream().map(this::mapTo).toList());
    if (detail.getFailureReason() != null) {
      info.setFailureReason(detail.getFailureReason());
    }
    info.setPhysicalTenants(aggregated.perTenant().stream().map(this::mapTo).toList());

    return info;
  }

  private HistoryBackupTenantInfo mapTo(final TenantBackupState tenantState) {
    final var detail = tenantState.dto();
    final var details =
        detail.getDetails() == null
            ? List.<HistoryBackupDetail>of()
            : detail.getDetails().stream().map(this::mapTo).toList();
    final var tenantInfo =
        new HistoryBackupTenantInfo(
            tenantState.physicalTenantId(), mapState(detail.getState()), details);
    if (detail.getFailureReason() != null) {
      tenantInfo.setFailureReason(detail.getFailureReason());
    }
    return tenantInfo;
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

  private record TenantBackupState(String physicalTenantId, GetBackupStateResponseDto dto) {}

  private record AggregatedBackupState(
      GetBackupStateResponseDto overall, List<TenantBackupState> perTenant) {}
}
