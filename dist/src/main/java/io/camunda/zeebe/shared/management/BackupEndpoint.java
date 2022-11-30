/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.admin.backup.BackupApi;
import io.camunda.zeebe.gateway.admin.backup.BackupRequestHandler;
import io.camunda.zeebe.gateway.admin.backup.BackupStatus;
import io.camunda.zeebe.gateway.admin.backup.PartitionBackupStatus;
import io.camunda.zeebe.gateway.admin.backup.State;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.shared.management.openapi.models.BackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.Error;
import io.camunda.zeebe.shared.management.openapi.models.PartitionBackupInfo;
import io.camunda.zeebe.shared.management.openapi.models.StateCode;
import io.camunda.zeebe.util.VisibleForTesting;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backups", enableByDefault = false)
final class BackupEndpoint {
  private final BackupApi api;

  @SuppressWarnings("unused") // used by Spring
  @Autowired
  public BackupEndpoint(final BrokerClient client) {
    this(new BackupRequestHandler(client));
  }

  BackupEndpoint(final BackupApi api) {
    this.api = api;
  }

  @WriteOperation
  public WebEndpointResponse<?> take(@Selector @NonNull final long id) {
    try {
      final long backupId = api.takeBackup(id).toCompletableFuture().join();
      return new WebEndpointResponse<>(new TakeBackupResponse(backupId));
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getCause().getMessage()),
          WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          new ErrorResponse(id, e.getMessage()), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> status(@Selector @NonNull final long id) {
    try {
      final BackupStatus status = api.getStatus(id).toCompletableFuture().join();
      if (status.status() == State.DOES_NOT_EXIST) {
        return doestNotExistResponse(status.backupId());
      }
      final BackupInfo backupInfo = getBackupInfoFromBackupStatus(status);
      return new WebEndpointResponse<>(backupInfo);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          new Error().message(e.getCause().getMessage()),
          WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          new Error().message(e.getMessage()), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> list() {
    try {
      final var backups = api.listBackups().toCompletableFuture().join();
      final var response = backups.stream().map(this::getBackupInfoFromBackupStatus).toList();
      return new WebEndpointResponse<>(response);
    } catch (final CompletionException e) {
      return new WebEndpointResponse<>(
          new Error().message(e.getCause().getMessage()),
          WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    } catch (final Exception e) {
      return new WebEndpointResponse<>(
          new Error().message(e.getMessage()), WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR);
    }
  }

  private BackupInfo getBackupInfoFromBackupStatus(final BackupStatus status) {
    final BackupInfo backupInfo =
        new BackupInfo().backupId(status.backupId()).state(getBackupStateCode(status.status()));
    status.failureReason().ifPresent(backupInfo::setFailureReason);
    final var details = status.partitions().stream().map(this::toPartitionBackupInfo).toList();
    backupInfo.setDetails(details);
    return backupInfo;
  }

  private static WebEndpointResponse<Error> doestNotExistResponse(final long backupId) {
    return new WebEndpointResponse<>(
        new Error().message("Backup with id %d does not exist".formatted(backupId)),
        WebEndpointResponse.STATUS_NOT_FOUND);
  }

  private PartitionBackupInfo toPartitionBackupInfo(final PartitionBackupStatus partitionStatus) {
    final var partitionBackupInfo =
        new PartitionBackupInfo()
            .partitionId(partitionStatus.partitionId())
            .state(getPartitionBackupStateCode(partitionStatus.status()));
    partitionStatus.failureReason().ifPresent(partitionBackupInfo::setFailureReason);
    partitionStatus
        .createdAt()
        .ifPresent(
            time -> {
              final var i = Instant.parse(time);
              partitionBackupInfo.createdAt(OffsetDateTime.ofInstant(i, ZoneId.of("UTC")));
            });
    partitionStatus
        .lastUpdatedAt()
        .ifPresent(
            time -> {
              final var i = Instant.parse(time);
              partitionBackupInfo.lastUpdatedAt(OffsetDateTime.ofInstant(i, ZoneId.of("UTC")));
            });
    partitionStatus.brokerId().ifPresent(partitionBackupInfo::setBrokerId);
    partitionStatus.brokerVersion().ifPresent(partitionBackupInfo::setBrokerVersion);
    partitionStatus.snapshotId().ifPresent(partitionBackupInfo::setSnapshotId);
    partitionStatus.checkpointPosition().ifPresent(partitionBackupInfo::setCheckpointPosition);
    return partitionBackupInfo;
  }

  private StateCode getBackupStateCode(final State state) {
    return switch (state) {
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
      case FAILED -> StateCode.FAILED;
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      case INCOMPLETE -> StateCode.INCOMPLETE;
    };
  }

  private StateCode getPartitionBackupStateCode(final BackupStatusCode status) {
    return switch (status) {
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
      case FAILED -> StateCode.FAILED;
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      default -> throw new IllegalStateException("Unknown BackupState %s".formatted(status));
    };
  }

  @VisibleForTesting
  record ErrorResponse(long id, String failure) {}

  @VisibleForTesting
  record TakeBackupResponse(long id) {}
}
