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
import io.camunda.gateway.protocol.model.ClusterHistoryBackupInfo;
import io.camunda.gateway.protocol.model.ClusterHistoryBackupTakeResult;
import io.camunda.gateway.protocol.model.ClusterHistoryBackupTenantInfo;
import io.camunda.gateway.protocol.model.ClusterHistoryBackupTenantState;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupInfo;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupState;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupTakeOutcome;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupTakeResult;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupTenantInfo;
import io.camunda.gateway.protocol.model.ClusterRuntimeBackupTenantState;
import io.camunda.gateway.protocol.model.ClusterTakeHistoryBackupResponse;
import io.camunda.gateway.protocol.model.ClusterTakeRuntimeBackupResponse;
import io.camunda.gateway.protocol.model.HistoryBackupInfo;
import io.camunda.gateway.protocol.model.HistoryBackupSnapshotInfo;
import io.camunda.gateway.protocol.model.HistoryBackupStateCode;
import io.camunda.gateway.protocol.model.PartitionBackupInfo;
import io.camunda.gateway.protocol.model.PartitionBackupRange;
import io.camunda.gateway.protocol.model.PartitionBackupState;
import io.camunda.gateway.protocol.model.PartitionCheckpointState;
import io.camunda.gateway.protocol.model.RuntimeBackupState;
import io.camunda.gateway.protocol.model.StateCode;
import io.camunda.gateway.protocol.model.TakeHistoryBackupResponse;
import io.camunda.gateway.protocol.model.TakeRuntimeBackupResponse;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackup;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupState;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.TenantBackupStateCode;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupStates;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.TakeOutcome;
import io.camunda.service.RuntimeBackupServices;
import io.camunda.service.backup.HistoryBackupSnapshot;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupTaken;
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
      final RuntimeBackupServices.RuntimeBackupState state) {
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

  public static TakeHistoryBackupResponse toTakeHistoryBackupResponse(
      final HistoryBackupTaken taken) {
    return TakeHistoryBackupResponse.Builder.create()
        .backupId(taken.backupId())
        .scheduledSnapshots(taken.scheduledSnapshots())
        .build();
  }

  public static HistoryBackupInfo toHistoryBackupInfo(final HistoryBackupState state) {
    return HistoryBackupInfo.Builder.create()
        .backupId(state.backupId())
        .state(toHistoryBackupStateCode(state.state()))
        .failureReason(state.failureReason())
        .details(state.snapshots().stream().map(BackupResponseMapper::toSnapshotInfo).toList())
        .build();
  }

  public static List<HistoryBackupInfo> toHistoryBackupInfoList(
      final List<HistoryBackupState> states) {
    return states.stream().map(BackupResponseMapper::toHistoryBackupInfo).toList();
  }

  private static HistoryBackupSnapshotInfo toSnapshotInfo(final HistoryBackupSnapshot snapshot) {
    return HistoryBackupSnapshotInfo.Builder.create()
        .snapshotName(snapshot.snapshotName())
        .state(snapshot.state())
        .startTime(
            snapshot.startTime() == null ? null : ResponseMapper.formatDate(snapshot.startTime()))
        .failures(snapshot.failures())
        .build();
  }

  private static HistoryBackupStateCode toHistoryBackupStateCode(
      final io.camunda.service.backup.HistoryBackupStateCode state) {
    return switch (state) {
      case IN_PROGRESS -> HistoryBackupStateCode.IN_PROGRESS;
      case COMPLETED -> HistoryBackupStateCode.COMPLETED;
      case FAILED -> HistoryBackupStateCode.FAILED;
      case INCOMPLETE -> HistoryBackupStateCode.INCOMPLETE;
      case INCOMPATIBLE -> HistoryBackupStateCode.INCOMPATIBLE;
    };
  }

  public static ClusterTakeRuntimeBackupResponse toClusterTakeRuntimeBackupResponse(
      final ClusterRuntimeBackupTaken taken) {
    return ClusterTakeRuntimeBackupResponse.Builder.create()
        .physicalTenants(
            taken.physicalTenants().stream()
                .map(BackupResponseMapper::toClusterRuntimeBackupTakeResult)
                .toList())
        .build();
  }

  private static ClusterRuntimeBackupTakeResult toClusterRuntimeBackupTakeResult(
      final PhysicalTenantRuntimeBackupTaken taken) {
    return ClusterRuntimeBackupTakeResult.Builder.create()
        .physicalTenantId(taken.physicalTenantId())
        .backupId(taken.backupId())
        .outcome(toClusterRuntimeBackupTakeOutcome(taken.outcome()))
        .reason(taken.reason())
        .build();
  }

  private static ClusterRuntimeBackupTakeOutcome toClusterRuntimeBackupTakeOutcome(
      final TakeOutcome outcome) {
    return switch (outcome) {
      case TRIGGERED -> ClusterRuntimeBackupTakeOutcome.TRIGGERED;
      case FAILED -> ClusterRuntimeBackupTakeOutcome.FAILED;
      case UNKNOWN -> ClusterRuntimeBackupTakeOutcome.UNKNOWN;
    };
  }

  public static ClusterRuntimeBackupInfo toClusterRuntimeBackupInfo(
      final ClusterRuntimeBackup backup) {
    return ClusterRuntimeBackupInfo.Builder.create()
        .backupId(backup.backupId())
        .state(toStateCode(backup.state()))
        .failureReason(backup.failureReason())
        .physicalTenants(
            backup.physicalTenants().stream()
                .map(BackupResponseMapper::toClusterRuntimeBackupTenantInfo)
                .toList())
        .build();
  }

  public static List<ClusterRuntimeBackupInfo> toClusterRuntimeBackupInfoList(
      final List<ClusterRuntimeBackup> backups) {
    return backups.stream().map(BackupResponseMapper::toClusterRuntimeBackupInfo).toList();
  }

  private static ClusterRuntimeBackupTenantInfo toClusterRuntimeBackupTenantInfo(
      final PhysicalTenantRuntimeBackup backup) {
    final var status = backup.backup();
    return ClusterRuntimeBackupTenantInfo.Builder.create()
        .physicalTenantId(backup.physicalTenantId())
        .state(toStateCode(status.status()))
        .failureReason(status.failureReason().orElse(null))
        .details(
            status.partitions().stream().map(BackupResponseMapper::toPartitionBackupInfo).toList())
        .build();
  }

  public static ClusterRuntimeBackupState toClusterRuntimeBackupState(
      final ClusterRuntimeBackupStates states) {
    return ClusterRuntimeBackupState.Builder.create()
        .physicalTenants(
            states.physicalTenants().stream()
                .map(
                    state ->
                        ClusterRuntimeBackupTenantState.Builder.create()
                            .physicalTenantId(state.physicalTenantId())
                            .state(toRuntimeBackupState(state.state()))
                            .build())
                .toList())
        .build();
  }

  public static ClusterTakeHistoryBackupResponse toClusterTakeHistoryBackupResponse(
      final ClusterHistoryBackupTaken taken) {
    return ClusterTakeHistoryBackupResponse.Builder.create()
        .backupId(taken.backupId())
        .physicalTenants(
            taken.physicalTenants().stream()
                .map(BackupResponseMapper::toClusterHistoryBackupTakeResult)
                .toList())
        .build();
  }

  private static ClusterHistoryBackupTakeResult toClusterHistoryBackupTakeResult(
      final PhysicalTenantBackupTaken taken) {
    return ClusterHistoryBackupTakeResult.Builder.create()
        .physicalTenantId(taken.physicalTenantId())
        .scheduledSnapshots(taken.scheduledSnapshots())
        .build();
  }

  public static ClusterHistoryBackupInfo toClusterHistoryBackupInfo(
      final ClusterHistoryBackup backup) {
    return ClusterHistoryBackupInfo.Builder.create()
        .backupId(backup.backupId())
        .physicalTenants(
            backup.physicalTenants().stream()
                .map(BackupResponseMapper::toClusterHistoryBackupTenantInfo)
                .toList())
        .build();
  }

  public static List<ClusterHistoryBackupInfo> toClusterHistoryBackupInfoList(
      final List<ClusterHistoryBackup> backups) {
    return backups.stream().map(BackupResponseMapper::toClusterHistoryBackupInfo).toList();
  }

  private static ClusterHistoryBackupTenantInfo toClusterHistoryBackupTenantInfo(
      final PhysicalTenantBackupState state) {
    return ClusterHistoryBackupTenantInfo.Builder.create()
        .physicalTenantId(state.physicalTenantId())
        .state(toClusterHistoryBackupTenantState(state.state()))
        .failureReason(state.failureReason())
        .details(state.snapshots().stream().map(BackupResponseMapper::toSnapshotInfo).toList())
        .build();
  }

  private static ClusterHistoryBackupTenantState toClusterHistoryBackupTenantState(
      final TenantBackupStateCode state) {
    return switch (state) {
      case IN_PROGRESS -> ClusterHistoryBackupTenantState.IN_PROGRESS;
      case COMPLETED -> ClusterHistoryBackupTenantState.COMPLETED;
      case FAILED -> ClusterHistoryBackupTenantState.FAILED;
      case INCOMPLETE -> ClusterHistoryBackupTenantState.INCOMPLETE;
      case INCOMPATIBLE -> ClusterHistoryBackupTenantState.INCOMPATIBLE;
      case NOT_FOUND -> ClusterHistoryBackupTenantState.NOT_FOUND;
    };
  }
}
