/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import static java.util.stream.Collectors.toSet;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.logarchive.leader.LogArchiveActivation;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.PartitionBackupRange;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionLogArchiveState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogArchiveRecord;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.protocol.record.intent.management.LogArchiveIntent;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Request handler to handle commands and queries related to the backup ({@link
 * RequestType#BACKUP}). Extends the read-only {@link ReadOnlyBackupApiRequestHandler} to support
 * mutating request types (take, delete, query/sync/clear state)
 */
public final class BackupApiRequestHandler extends ReadOnlyBackupApiRequestHandler
    implements DiskSpaceUsageListener {
  private boolean isDiskSpaceAvailable = true;
  private final LogStreamWriter logStreamWriter;
  private final BackupManager backupManager;
  private final CheckpointState checkpointState;
  private final DbCheckpointMetadataState checkpointMetadataState;
  private final DbBackupRangeState backupRangeState;
  private final DbLogArchiveState logArchiveState;
  private final CheckpointSnapshotReserver snapshotReserver;
  private final @Nullable LogArchiveActivation logArchiveActivation;
  private final BrokerMemberId memberId;

  public BackupApiRequestHandler(
      final AtomixServerTransport transport,
      final LogStreamWriter logStreamWriter,
      final BackupManager backupManager,
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final DbLogArchiveState logArchiveState,
      final PersistedSnapshotStore snapshotStore,
      final SnapshotTrigger snapshotTrigger,
      final PartitionId partition,
      final BrokerMemberId memberId,
      final boolean backupFeatureEnabled,
      final @Nullable LogArchiveActivation logArchiveActivation) {
    super("BackupApi", backupManager, partition, transport, backupFeatureEnabled);
    this.logArchiveActivation = logArchiveActivation;
    this.memberId = memberId;
    this.logStreamWriter = logStreamWriter;
    this.backupManager = backupManager;
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
    this.logArchiveState = logArchiveState;
    snapshotReserver =
        new CheckpointSnapshotReserver(snapshotStore, snapshotTrigger, checkpointState, actor);
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> handleAsync(
      final int requestStreamId,
      final long requestId,
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {

    if (!backupFeatureEnabled) {
      return CompletableActorFuture.completed(backupFeatureDisabledError(errorWriter));
    }

    return switch (requestReader.type()) {
      case TAKE_BACKUP ->
          handleTakeBackupRequest(
              requestStreamId, requestId, requestReader, responseWriter, errorWriter);
      case QUERY_STATUS -> handleQueryStatusRequest(requestReader, responseWriter, errorWriter);
      case LIST -> handleListBackupRequest(requestReader, responseWriter, errorWriter);
      case DELETE -> handleDeleteBackupRequest(requestReader, responseWriter, errorWriter);
      case QUERY_STATE -> handleQueryStateRequest(responseWriter, errorWriter);
      case QUERY_RANGES -> handleQueryRangesRequest(responseWriter, errorWriter);
      case SYNC_METADATA -> handleSyncMetadataRequest(responseWriter, errorWriter);
      case CLEAR_STATE -> handleClearStateRequest(responseWriter, errorWriter);
      case ACTIVATE_LOG_ARCHIVE -> handleActivateLogArchiveRequest(responseWriter, errorWriter);
      case COMPLETE_HANDOVER ->
          handleCompleteHandoverRequest(requestReader, responseWriter, errorWriter);
      default ->
          CompletableActorFuture.completed(unknownRequest(errorWriter, requestReader.type()));
    };
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> handleTakeBackupRequest(
      final int requestStreamId,
      final long requestId,
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    if (!isDiskSpaceAvailable) {
      return CompletableActorFuture.completed(
          Either.left(errorWriter.outOfDiskSpace(partitionId.number())));
    }

    final var checkpointId = requestReader.backupId();
    final var checkpointType = requestReader.checkpointType();
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        actor.createFuture();

    snapshotReserver
        .reserveFor(checkpointId, checkpointType)
        .onComplete(
            (snapshotId, ignored) -> {
              final var written =
                  writeCheckpoint(
                      requestStreamId, requestId, checkpointId, checkpointType, snapshotId);
              if (written.isRight()) {
                result.complete(Either.right(responseWriter.noResponse()));
              } else {
                snapshotReserver.release(checkpointId, snapshotId);
                result.complete(
                    Either.left(
                        errorWriter.mapWriteError(partitionId.number(), written.getLeft())));
              }
            },
            actor);
    return result;
  }

  private Either<WriteFailure, Long> writeCheckpoint(
      final int requestStreamId,
      final long requestId,
      final long checkpointId,
      final CheckpointType checkpointType,
      final Optional<String> snapshotId) {
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.CREATE)
            .requestId(requestId)
            .requestStreamId(requestStreamId);
    final var checkpointRecord =
        new CheckpointRecord()
            .setCheckpointId(checkpointId)
            .setCheckpointType(checkpointType)
            .setSnapshotId(snapshotId.orElse(""));
    return logStreamWriter.tryWrite(
        WriteContext.internal(), LogAppendEntry.of(metadata, checkpointRecord));
  }

  /**
   * A checkpoint after the handover has no old-style backup; the log archive answers for it: {@code
   * COMPLETED} once the archived position reached the checkpoint, {@code IN_PROGRESS} before. A
   * checkpoint created between the handover checkpoint and the recorded handover can still have an
   * old backup, so the old store is asked first.
   */
  @Override
  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryStatusRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final long checkpointId = requestReader.backupId();
    if (!logArchiveState.meta().isAfterHandover(checkpointId)) {
      return super.handleQueryStatusRequest(requestReader, responseWriter, errorWriter);
    }

    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        actor.createFuture();
    backupManager
        .getBackupStatus(checkpointId)
        .onComplete(
            (status, error) -> {
              if (error != null) {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              } else if (encodeStatusCode(status.statusCode()) != BackupStatusCode.DOES_NOT_EXIST) {
                result.complete(Either.right(responseWriter.withStatus(buildResponse(status))));
              } else {
                result.complete(
                    Either.right(responseWriter.withStatus(archiveStatusResponse(checkpointId))));
              }
            },
            actor);
    return result;
  }

  private BackupStatusResponse archiveStatusResponse(final long checkpointId) {
    final var response =
        new BackupStatusResponse()
            .setBackupId(checkpointId)
            .setBrokerId(memberId.nodeIdx(), memberId.zone())
            .setPartitionId(partitionId.number());
    final var checkpoint = logArchiveState.findCheckpoint(checkpointId);
    if (checkpoint.isEmpty()) {
      return response.setStatus(BackupStatusCode.DOES_NOT_EXIST);
    }
    return fillArchiveStatus(
        response, checkpoint.get(), logArchiveState.meta().getArchivedPosition());
  }

  private static BackupStatusResponse fillArchiveStatus(
      final BackupStatusResponse response,
      final CoveredCheckpoint checkpoint,
      final long archivedPosition) {
    // a checkpoint without a position lies in a sealed chunk, which is archived by definition
    final boolean archived =
        checkpoint.getCheckpointPosition() < 0
            || archivedPosition >= checkpoint.getCheckpointPosition();
    response.setStatus(archived ? BackupStatusCode.COMPLETED : BackupStatusCode.IN_PROGRESS);
    if (checkpoint.getCheckpointPosition() >= 0) {
      response.setCheckpointPosition(checkpoint.getCheckpointPosition());
    }
    if (checkpoint.getCheckpointTimestamp() >= 0) {
      response.setCreatedAt(Instant.ofEpochMilli(checkpoint.getCheckpointTimestamp()).toString());
    }
    return response;
  }

  /**
   * Lists the old backups together with the log archive's backup checkpoints after the handover,
   * ordered by id. An old backup wins over an archive checkpoint with the same id.
   */
  @Override
  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleListBackupRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final var meta = logArchiveState.meta();
    if (!meta.hasHandover()) {
      return super.handleListBackupRequest(requestReader, responseWriter, errorWriter);
    }

    // the reader is reused for the next request, so it must not be read in the callback
    final var requestedPattern = requestReader.pattern();
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        actor.createFuture();
    backupManager
        .listBackups(requestedPattern)
        .onComplete(
            (backups, error) -> {
              if (error != null) {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
                return;
              }
              final var statuses = new ArrayList<>(buildBackupListResponse(backups).getBackups());
              final var oldIds =
                  statuses.stream().map(BackupListResponse.BackupStatus::backupId).collect(toSet());
              final var pattern = CheckpointPattern.of(requestedPattern);
              final long archivedPosition = meta.getArchivedPosition();
              logArchiveState.checkpointsAfter(meta.getHandoverCheckpointId()).stream()
                  .filter(checkpoint -> checkpoint.getCheckpointType().shouldCreateBackup())
                  .filter(checkpoint -> pattern.matches(checkpoint.getCheckpointId()))
                  .filter(checkpoint -> !oldIds.contains(checkpoint.getCheckpointId()))
                  .forEach(
                      checkpoint -> {
                        final var archive =
                            fillArchiveStatus(
                                new BackupStatusResponse(), checkpoint, archivedPosition);
                        statuses.add(
                            new BackupListResponse.BackupStatus(
                                checkpoint.getCheckpointId(),
                                partitionId.number(),
                                archive.getStatus(),
                                "",
                                "",
                                Objects.requireNonNullElse(archive.getCreatedAt(), "")));
                      });
              statuses.sort(Comparator.comparingLong(BackupListResponse.BackupStatus::backupId));
              result.complete(
                  Either.right(responseWriter.withBackupList(new BackupListResponse(statuses))));
            },
            actor);
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleDeleteBackupRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var backupId = requestReader.backupId();
    backupManager
        .requestBackupDeletion(backupId)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                final BackupStatusResponse response =
                    new BackupStatusResponse()
                        .setBackupId(backupId)
                        .setStatus(BackupStatusCode.DOES_NOT_EXIST)
                        .setPartitionId(requestReader.partitionId());
                result.complete(Either.right(responseWriter.withStatus(response)));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            });
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> handleQueryStateRequest(
      final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var response = new CheckpointStateResponse();

    if (checkpointState.getLatestBackupId() != CheckpointState.NO_CHECKPOINT) {
      final PartitionCheckpointState backupState =
          new PartitionCheckpointState(
              partitionId.number(),
              checkpointState.getLatestBackupId(),
              checkpointState.getLatestBackupType(),
              checkpointState.getLatestBackupTimestamp(),
              checkpointState.getLatestBackupPosition(),
              checkpointState.getLatestBackupFirstLogPosition());
      response.getBackupStates().add(backupState);
    }
    if (checkpointState.getLatestCheckpointId() != CheckpointState.NO_CHECKPOINT) {
      final PartitionCheckpointState cpState =
          new PartitionCheckpointState(
              partitionId.number(),
              checkpointState.getLatestCheckpointId(),
              checkpointState.getLatestCheckpointType(),
              checkpointState.getLatestCheckpointTimestamp(),
              checkpointState.getLatestCheckpointPosition());
      response.getCheckpointStates().add(cpState);
    }
    response.getLogArchiveStates().add(logArchiveStateOfPartition());
    result.complete(Either.right(responseWriter.withCheckpointState(response)));
    return result;
  }

  private PartitionLogArchiveState logArchiveStateOfPartition() {
    final var meta = logArchiveState.meta();
    final long newestSnapshotPosition =
        logArchiveState.snapshots().stream()
            .max(Comparator.comparingLong(ArchivedSnapshot::getProcessedPosition))
            .map(ArchivedSnapshot::getProcessedPosition)
            .orElse(-1L);
    return new PartitionLogArchiveState(
        partitionId.number(),
        !meta.getTimelineId().isEmpty(),
        meta.getArchivedPosition(),
        newestSnapshotPosition,
        meta.getHandoverCheckpointId());
  }

  /**
   * Writes the command; whether the handover is accepted is decided when the log archive processor
   * processes it, and shows in the checkpoint state. The response only acknowledges the write.
   */
  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleCompleteHandoverRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final long checkpointId = requestReader.backupId();
    final var written =
        logStreamWriter.tryWrite(
            WriteContext.internal(),
            LogAppendEntry.of(
                new RecordMetadata()
                    .recordType(RecordType.COMMAND)
                    .valueType(ValueType.LOG_ARCHIVE)
                    .intent(LogArchiveIntent.COMPLETE_HANDOVER),
                new LogArchiveRecord().setHandoverCheckpointId(checkpointId)));
    if (written.isLeft()) {
      return CompletableActorFuture.completed(
          Either.left(errorWriter.mapWriteError(partitionId.number(), written.getLeft())));
    }
    final var response =
        new BackupStatusResponse()
            .setBackupId(checkpointId)
            .setStatus(BackupStatusCode.IN_PROGRESS)
            .setPartitionId(partitionId.number());
    return CompletableActorFuture.completed(Either.right(responseWriter.withStatus(response)));
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleSyncMetadataRequest(
          final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var checkpoints = checkpointMetadataState.getAllCheckpoints();
    final var ranges = backupRangeState.getAllRanges();
    backupManager
        .syncMetadata(checkpoints, ranges)
        .onComplete(
            (syncedRanges, error) -> {
              if (error == null) {
                final var response = new BackupRangesResponse();
                response.setRanges(
                    syncedRanges.stream()
                        .map(
                            r ->
                                new PartitionBackupRange(
                                    partitionId.number(),
                                    fromCheckpointInfo(r.first()),
                                    fromCheckpointInfo(r.last())))
                        .toList());
                result.complete(Either.right(responseWriter.withBackupRanges(response)));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            });
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> handleClearStateRequest(
      final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupManager
        .requestStateClear()
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                final BackupStatusResponse response =
                    new BackupStatusResponse()
                        .setStatus(BackupStatusCode.DOES_NOT_EXIST)
                        .setPartitionId(partitionId.number());
                result.complete(Either.right(responseWriter.withStatus(response)));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            });
    return result;
  }

  /**
   * Activates the log archive of this partition (spec section 18.1) and answers with {@code
   * COMPLETED} once the partition's state holds a started timeline, {@code IN_PROGRESS} while the
   * start is pending, and {@code DOES_NOT_EXIST} if the log archive is disabled on this broker.
   */
  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleActivateLogArchiveRequest(
          final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final var activation = logArchiveActivation;
    if (activation == null) {
      return CompletableActorFuture.completed(
          Either.right(
              responseWriter.withStatus(
                  new BackupStatusResponse()
                      .setStatus(BackupStatusCode.DOES_NOT_EXIST)
                      .setPartitionId(partitionId.number()))));
    }
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        actor.createFuture();
    activation
        .activate()
        .onComplete(
            (timelineStarted, error) -> {
              if (error == null) {
                final var response =
                    new BackupStatusResponse()
                        .setStatus(
                            timelineStarted
                                ? BackupStatusCode.COMPLETED
                                : BackupStatusCode.IN_PROGRESS)
                        .setPartitionId(partitionId.number());
                result.complete(Either.right(responseWriter.withStatus(response)));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            },
            actor);
    return result;
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> unknownRequest(
      final ErrorResponseWriter errorWriter, final BackupRequestType type) {
    errorWriter.unsupportedMessage(type, AdminRequestType.values());
    return Either.left(errorWriter);
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.submit(() -> isDiskSpaceAvailable = false);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.submit(() -> isDiskSpaceAvailable = true);
  }
}
