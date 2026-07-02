/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.backup.processing.state.DbBackupRangeState;
import io.camunda.zeebe.backup.processing.state.DbCheckpointMetadataState;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.PartitionBackupRange;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.management.AdminRequestType;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;

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

  public BackupApiRequestHandler(
      final AtomixServerTransport transport,
      final LogStreamWriter logStreamWriter,
      final BackupManager backupManager,
      final CheckpointState checkpointState,
      final DbCheckpointMetadataState checkpointMetadataState,
      final DbBackupRangeState backupRangeState,
      final PartitionId partition,
      final boolean backupFeatureEnabled) {
    super("BackupApi",backupManager, partition, transport, backupFeatureEnabled);
    this.logStreamWriter = logStreamWriter;
    this.backupManager = backupManager;
    this.checkpointState = checkpointState;
    this.checkpointMetadataState = checkpointMetadataState;
    this.backupRangeState = backupRangeState;
  }

  @Override
  public String getName() {
    return "BackupApiRequestHandler";
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
          CompletableActorFuture.completed(
              handleTakeBackupRequest(
                  requestStreamId, requestId, requestReader, responseWriter, errorWriter));
      case QUERY_STATUS -> handleQueryStatusRequest(requestReader, responseWriter, errorWriter);
      case LIST -> handleListBackupRequest(requestReader, responseWriter, errorWriter);
      case DELETE -> handleDeleteBackupRequest(requestReader, responseWriter, errorWriter);
      case QUERY_STATE -> handleQueryStateRequest(responseWriter, errorWriter);
      case QUERY_RANGES -> handleQueryRangesRequest(responseWriter, errorWriter);
      case SYNC_METADATA -> handleSyncMetadataRequest(responseWriter, errorWriter);
      case CLEAR_STATE -> handleClearStateRequest(responseWriter, errorWriter);
      default ->
          CompletableActorFuture.completed(unknownRequest(errorWriter, requestReader.type()));
    };
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> handleTakeBackupRequest(
      final int requestStreamId,
      final long requestId,
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    if (!isDiskSpaceAvailable) {
      return Either.left(errorWriter.outOfDiskSpace(partitionId.number()));
    }

    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.CREATE)
            .requestId(requestId)
            .requestStreamId(requestStreamId);
    final var checkpointRecord =
        new CheckpointRecord()
            .setCheckpointId(requestReader.backupId())
            .setCheckpointType(requestReader.checkpointType());
    final var written =
        logStreamWriter.tryWrite(
            WriteContext.internal(), LogAppendEntry.of(metadata, checkpointRecord));

    if (written.isRight()) {
      // Response will be sent by the processor
      return Either.right(responseWriter.noResponse());
    } else {
      return Either.left(errorWriter.mapWriteError(partitionId.number(), written.getLeft()));
    }
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
    result.complete(Either.right(responseWriter.withCheckpointState(response)));
    return result;
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
