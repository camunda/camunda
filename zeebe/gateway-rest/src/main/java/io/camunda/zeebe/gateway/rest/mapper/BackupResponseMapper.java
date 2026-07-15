/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.BackupInfo;
import io.camunda.gateway.protocol.model.BackupType;
import io.camunda.gateway.protocol.model.CheckpointType;
import io.camunda.gateway.protocol.model.PartitionBackupInfo;
import io.camunda.gateway.protocol.model.PartitionBackupRange;
import io.camunda.gateway.protocol.model.PartitionBackupState;
import io.camunda.gateway.protocol.model.PartitionCheckpointState;
import io.camunda.gateway.protocol.model.RuntimeBackupState;
import io.camunda.gateway.protocol.model.StateCode;
import io.camunda.gateway.protocol.model.TakeRuntimeBackupResponse;
import io.camunda.service.BackupServices;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class BackupResponseMapper {

  private BackupResponseMapper() {}

  public static TakeRuntimeBackupResponse toTakeBackupResponse(final long backupId) {
    return TakeRuntimeBackupResponse.Builder.create().backupId(backupId).build();
  }

  public static BackupInfo toBackupInfo(final BackupStatus status) {
    final var details =
        status.partitions().stream().map(BackupResponseMapper::toPartitionBackupInfo).toList();
    return BackupInfo.Builder.create()
        .backupId(status.backupId())
        .state(toStateCode(status.status()))
        .failureReason(status.failureReason().orElse(null))
        .details(details)
        .build();
  }

  public static List<BackupInfo> toBackupInfoList(final List<BackupStatus> statuses) {
    return statuses.stream().map(BackupResponseMapper::toBackupInfo).toList();
  }

  private static PartitionBackupInfo toPartitionBackupInfo(final PartitionBackupStatus status) {
    return PartitionBackupInfo.Builder.create()
        .partitionId(status.partitionId())
        .state(toStateCode(status.status()))
        .failureReason(status.failureReason().orElse(null))
        .createdAt(
            status
                .createdAt()
                .map(Instant::parse)
                .map(BackupResponseMapper::toDateString)
                .orElse(null))
        .lastUpdatedAt(
            status
                .lastUpdatedAt()
                .map(Instant::parse)
                .map(BackupResponseMapper::toDateString)
                .orElse(null))
        .snapshotId(status.snapshotId().orElse(null))
        .firstLogPosition(
            status.firstLogPosition().isPresent() ? status.firstLogPosition().getAsLong() : null)
        .checkpointPosition(
            status.checkpointPosition().isPresent()
                ? status.checkpointPosition().getAsLong()
                : null)
        .brokerId(status.brokerId().isPresent() ? status.brokerId().getAsInt() : null)
        .brokerVersion(status.brokerVersion().orElse(null))
        .build();
  }

  private static StateCode toStateCode(final State state) {
    return switch (state) {
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      case INCOMPLETE -> StateCode.INCOMPLETE;
      case FAILED -> StateCode.FAILED;
      case DELETED -> StateCode.DELETED;
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
    };
  }

  private static StateCode toStateCode(final BackupStatusCode status) {
    return switch (status) {
      case IN_PROGRESS -> StateCode.IN_PROGRESS;
      case COMPLETED -> StateCode.COMPLETED;
      case FAILED -> StateCode.FAILED;
      case DOES_NOT_EXIST -> StateCode.DOES_NOT_EXIST;
      case DELETED -> StateCode.DELETED;
      default -> throw new IllegalStateException("Unknown BackupState %s".formatted(status));
    };
  }

  public static RuntimeBackupState toRuntimeBackupState(
      final BackupServices.RuntimeBackupState state) {
    final var checkpointState = state.checkpointState();
    final var ranges = state.ranges();

    final var checkpointStates =
        mapResponse(
            checkpointState,
            CheckpointStateResponse::getCheckpointStates,
            BackupResponseMapper::toPartitionCheckpointState,
            Comparator.comparingInt(PartitionCheckpointState::getPartitionId));
    final var backupStates =
        mapResponse(
            checkpointState,
            CheckpointStateResponse::getBackupStates,
            BackupResponseMapper::toPartitionBackupState,
            Comparator.comparingInt(PartitionBackupState::getPartitionId));
    final var rangeState =
        mapResponse(
            ranges,
            BackupRangesResponse::getRanges,
            BackupResponseMapper::toPartitionBackupRange,
            Comparator.comparingInt(PartitionBackupRange::getPartitionId));

    return RuntimeBackupState.Builder.create()
        .checkpointStates(checkpointStates)
        .backupStates(backupStates)
        .ranges(rangeState)
        .build();
  }

  private static <S, T, R> List<R> mapResponse(
      final S source,
      final Function<S, ? extends Collection<T>> extractor,
      final Function<T, R> mapper,
      final Comparator<R> comparator) {
    return extractor.apply(source).stream().map(mapper).sorted(comparator).toList();
  }

  private static PartitionCheckpointState toPartitionCheckpointState(
      final CheckpointStateResponse.PartitionCheckpointState state) {
    return PartitionCheckpointState.Builder.create()
        .checkpointId(state.checkpointId())
        .checkpointType(toCheckpointType(state.checkpointType()))
        .partitionId(state.partitionId())
        .checkpointPosition(state.checkpointPosition())
        .checkpointTimestamp(toDateString(Instant.ofEpochMilli(state.checkpointTimestamp())))
        .build();
  }

  private static PartitionBackupState toPartitionBackupState(
      final CheckpointStateResponse.PartitionCheckpointState state) {
    return PartitionBackupState.Builder.create()
        .checkpointId(state.checkpointId())
        .checkpointType(toBackupType(state.checkpointType()))
        .partitionId(state.partitionId())
        .checkpointPosition(state.checkpointPosition())
        .firstLogPosition(state.firstLogPosition())
        .checkpointTimestamp(toDateString(Instant.ofEpochMilli(state.checkpointTimestamp())))
        .build();
  }

  private static PartitionBackupRange toPartitionBackupRange(
      final BackupRangesResponse.PartitionBackupRange range) {
    return PartitionBackupRange.Builder.create()
        .partitionId(range.partitionId())
        .start(range.first() == null ? null : toPartitionBackupState(range.first()))
        .end(range.last() == null ? null : toPartitionBackupState(range.last()))
        .build();
  }

  private static PartitionBackupState toPartitionBackupState(
      final BackupRangesResponse.CheckpointInfo info) {
    return PartitionBackupState.Builder.create()
        .checkpointId(info.checkpointId())
        .checkpointType(toBackupType(info.checkpointType()))
        .partitionId(null)
        .checkpointPosition(info.checkpointPosition())
        .firstLogPosition(info.firstLogPosition())
        .checkpointTimestamp(toDateString(info.checkpointTimestamp()))
        .build();
  }

  private static CheckpointType toCheckpointType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    return switch (checkpointType) {
      case SCHEDULED_BACKUP -> CheckpointType.SCHEDULED_BACKUP;
      case MANUAL_BACKUP -> CheckpointType.MANUAL_BACKUP;
      case MARKER -> CheckpointType.MARKER;
      case null -> null;
    };
  }

  private static BackupType toBackupType(
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType checkpointType) {
    return switch (checkpointType) {
      case MANUAL_BACKUP -> BackupType.MANUAL_BACKUP;
      case SCHEDULED_BACKUP -> BackupType.SCHEDULED_BACKUP;
      case MARKER -> null;
      case null -> null;
    };
  }

  private static String toDateString(final Instant instant) {
    return ResponseMapper.formatDate(OffsetDateTime.ofInstant(instant, ZoneId.of("UTC")));
  }
}
