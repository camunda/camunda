/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.BackupType;
import io.camunda.management.backups.CheckpointState;
import io.camunda.management.backups.CheckpointType;
import io.camunda.management.backups.Error;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.PartitionBackupRange;
import io.camunda.management.backups.PartitionBackupState;
import io.camunda.management.backups.PartitionCheckpointState;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.backup.client.api.BackupAlreadyExistException;
import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupRequestHandler;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.Selector.Match;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@WebEndpoint(id = "backupRuntime")
public final class BackupEndpoint {
  private final BackupApi api;
  private final BackupCfg backupCfg;

  @SuppressWarnings("unused") // used by Spring
  @Autowired
  public BackupEndpoint(final BrokerClient client, final BackupCfg backupCfg) {
    this(
        new BackupRequestHandler(client, new CheckpointIdGenerator(backupCfg.getOffset())),
        backupCfg);
  }

  BackupEndpoint(final BackupApi api, final BackupCfg backupCfg) {
    this.api = api;
    this.backupCfg = backupCfg;
  }

  @WriteOperation
  public WebEndpointResponse<?> take(final long backupId) {
    try {
      if (backupCfg.isContinuous()) {
        return new WebEndpointResponse<>(
            new Error()
                .message(
                    "Cannot take backup with predetermined backupId when continuous backups are enabled."
                        + " Use POST actuator/backupRuntime without specifying a backupId."),
            WebEndpointResponse.STATUS_BAD_REQUEST);
      }
      if (backupId <= 0) {
        return incorrectBackupIdErrorResponse();
      }
      api.takeBackup(backupId).toCompletableFuture().join();
      return successfullyScheduledBackupResponse(backupId);
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  @WriteOperation
  public WebEndpointResponse<?> take() {
    if (!backupCfg.isContinuous()) {
      return incorrectBackupIdErrorResponse();
    }

    try {
      final var backupId = api.takeBackup().toCompletableFuture().join();
      return successfullyScheduledBackupResponse(backupId);
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  @ReadOperation
  public WebEndpointResponse<?> listAll() {
    return listPrefix(BackupApi.WILDCARD);
  }

  @ReadOperation
  public WebEndpointResponse<?> query(
      @Selector(match = Match.ALL_REMAINING) final String[] arguments) {
    if (arguments.length > 1) {
      return new WebEndpointResponse<>(
          new Error().message("Invalid arguments provided."),
          WebEndpointResponse.STATUS_BAD_REQUEST);
    }
    final String argument = arguments[0];
    if (BackupApi.STATE.equals(argument)) {
      return state();
    }
    if (argument.endsWith(BackupApi.WILDCARD)) {
      return listPrefix(argument);
    }
    final long id;
    try {
      id = Long.parseLong(argument);
    } catch (final NumberFormatException e) {
      return new WebEndpointResponse<>(
          new Error()
              .message(
                  "Expected a backup ID or prefix ending with '*', but got '%s'."
                      .formatted(argument)),
          400);
    }
    return status(id);
  }

  @DeleteOperation
  public WebEndpointResponse<?> delete(@Selector @NonNull final long id) {
    try {
      api.deleteBackup(id).toCompletableFuture().join();
      return new WebEndpointResponse<>(WebEndpointResponse.STATUS_NO_CONTENT);
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  private WebEndpointResponse<?> state() {
    try {
      final var checkpointStateFuture = api.getCheckpointState().toCompletableFuture();
      final var rangesFuture = api.getBackupRanges().toCompletableFuture();
      final var checkpointState = checkpointStateFuture.join();
      final var ranges = rangesFuture.join();
      return new WebEndpointResponse<>(toCheckpointState(checkpointState, ranges));
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  private CheckpointState toCheckpointState(
      final CheckpointStateResponse checkpointState, final BackupRangesResponse ranges) {
    final var response = new CheckpointState();
    response.setCheckpointStates(
        checkpointState.getCheckpointStates().stream()
            .map(this::toCheckpointState)
            .sorted(Comparator.comparingInt(PartitionCheckpointState::getPartitionId))
            .toList());
    response.setBackupStates(
        checkpointState.getBackupStates().stream()
            .map(this::toBackupState)
            .sorted(Comparator.comparingInt(PartitionBackupState::getPartitionId))
            .toList());
    response.setRanges(
        ranges.getRanges().stream()
            .map(this::toRange)
            .sorted(Comparator.comparingInt(PartitionBackupRange::getPartitionId))
            .toList());
    return response;
  }

  private PartitionCheckpointState toCheckpointState(
      final CheckpointStateResponse.PartitionCheckpointState state) {
    final var response = new PartitionCheckpointState();
    response.setPartitionId(state.partitionId());
    response.setCheckpointId(state.checkpointId());
    response.setCheckpointPosition(state.checkpointPosition());
    response.setCheckpointTimestamp(
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(state.checkpointTimestamp()), ZoneId.of("UTC")));
    response.setCheckpointType(toCheckpointType(state.checkpointType()));
    return response;
  }

  private PartitionBackupState toBackupState(
      final CheckpointStateResponse.PartitionCheckpointState state) {
    final var response = new PartitionBackupState();
    response.setPartitionId(state.partitionId());
    response.setCheckpointId(state.checkpointId());
    response.setCheckpointPosition(state.checkpointPosition());
    response.setCheckpointTimestamp(
        OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(state.checkpointTimestamp()), ZoneId.of("UTC")));
    response.setCheckpointType(toBackupType(state.checkpointType()));
    return response;
  }

  private PartitionBackupRange toRange(final BackupRangesResponse.PartitionBackupRange range) {
    final var response = new PartitionBackupRange();
    response.setPartitionId(range.partitionId());
    response.setStart(range.first() == null ? null : range.first().checkpointId());
    response.setEnd(range.last() == null ? null : range.last().checkpointId());
    response.setMissingCheckpoints(range.missingCheckpoints().stream().toList());
    return response;
  }

  private CheckpointType toCheckpointType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    if (checkpointType == io.camunda.zeebe.protocol.record.value.management.CheckpointType.MARKER) {
      return CheckpointType.MARKER;
    }
    return null;
  }

  private BackupType toBackupType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    return switch (checkpointType) {
      case MANUAL_BACKUP -> BackupType.MANUAL_BACKUP;
      case SCHEDULED_BACKUP -> BackupType.SCHEDULED_BACKUP;
      case MARKER -> null;
    };
  }

  private WebEndpointResponse<?> status(final long id) {
    try {
      final BackupStatus status = api.getStatus(id).toCompletableFuture().join();
      if (status.status() == State.DOES_NOT_EXIST) {
        return doestNotExistResponse(status.backupId());
      }
      final BackupInfo backupInfo = getBackupInfoFromBackupStatus(status);
      return new WebEndpointResponse<>(backupInfo);
    } catch (final Exception e) {
      return mapErrorResponse(e);
    }
  }

  private WebEndpointResponse<?> listPrefix(final String prefix) {
    try {
      final var backups = api.listBackups(prefix).toCompletableFuture().join();
      final var response = backups.stream().map(this::getBackupInfoFromBackupStatus).toList();
      return new WebEndpointResponse<>(response);
    } catch (final Exception e) {
      return mapErrorResponse(e);
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
    partitionStatus.firstLogPosition().ifPresent(partitionBackupInfo::setFirstLogPosition);
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

  private WebEndpointResponse<Error> mapErrorResponse(final Throwable exception) {
    final int errorCode;
    final String message;
    if (exception instanceof CompletionException) {
      final var error = exception.getCause();
      if (error instanceof BackupAlreadyExistException) {
        errorCode = 409;
        message = error.getMessage();
      } else if (error instanceof IncompleteTopologyException) {
        errorCode = 502;
        message = error.getMessage();
      } else if (error instanceof TimeoutException || error instanceof ConnectTimeoutException) {
        errorCode = 504;
        message = "Request from gateway to broker timed out. " + error.getMessage();
      } else if (error instanceof ConnectException) {
        errorCode = 502;
        message = "Failed to send request from gateway to broker." + error.getMessage();
      } else if (error instanceof final BrokerErrorException brokerError) {
        final var rootError = brokerError.getError();
        errorCode =
            switch (rootError.getCode()) {
              case PARTITION_LEADER_MISMATCH -> 502;
              case RESOURCE_EXHAUSTED -> WebEndpointResponse.STATUS_SERVICE_UNAVAILABLE;
              case UNSUPPORTED_MESSAGE -> WebEndpointResponse.STATUS_BAD_REQUEST;
              default -> WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
            };
        message = rootError.getMessage();
      } else if (error instanceof final BrokerRejectionException brokerRejectionException) {
        errorCode = 409; // Conflict with concurrent scaling operation
        message =
            "Cannot take backup while scaling is in progress. Please retry after scaling is completed.";
      } else {
        errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
        message = error.getMessage();
      }
    } else {
      errorCode = WebEndpointResponse.STATUS_INTERNAL_SERVER_ERROR;
      message = exception.getMessage();
    }

    return new WebEndpointResponse<>(new Error().message(message), errorCode);
  }

  private WebEndpointResponse<TakeBackupRuntimeResponse> successfullyScheduledBackupResponse(
      final long backupId) {
    return new WebEndpointResponse<>(
        new TakeBackupRuntimeResponse()
            .message(
                "A backup with id %d has been scheduled. Use GET actuator/backups/%d to monitor the status."
                    .formatted(backupId, backupId)),
        202);
  }

  private WebEndpointResponse<?> incorrectBackupIdErrorResponse() {
    return new WebEndpointResponse<>(
        new Error().message("A backupId must be provided and it must be > 0"),
        WebEndpointResponse.STATUS_BAD_REQUEST);
  }
}
