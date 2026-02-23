/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.processing.state.CheckpointState;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
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
import java.time.Instant;
import java.util.Collection;

/**
 * Request handler to handle commands and queries related to the backup ({@link RequestType#BACKUP})
 */
public final class BackupApiRequestHandler
    extends AsyncApiRequestHandler<BackupApiRequestReader, BackupApiResponseWriter>
    implements DiskSpaceUsageListener {
  private boolean isDiskSpaceAvailable = true;
  private final LogStreamWriter logStreamWriter;
  private final BackupManager backupManager;
  private final AtomixServerTransport transport;
  private final CheckpointState checkpointState;
  private final int partitionId;
  private final boolean backupFeatureEnabled;

  public BackupApiRequestHandler(
      final AtomixServerTransport transport,
      final LogStreamWriter logStreamWriter,
      final BackupManager backupManager,
      final CheckpointState checkpointState,
      final int partitionId,
      final boolean backupFeatureEnabled) {
    super(BackupApiRequestReader::new, BackupApiResponseWriter::new);
    this.logStreamWriter = logStreamWriter;
    this.transport = transport;
    this.backupManager = backupManager;
    this.checkpointState = checkpointState;
    this.partitionId = partitionId;
    this.backupFeatureEnabled = backupFeatureEnabled;
    transport.unsubscribe(partitionId, RequestType.BACKUP);
    transport.subscribe(partitionId, RequestType.BACKUP, this);
  }

  @Override
  public void close() {
    transport.unsubscribe(partitionId, RequestType.BACKUP);
    // The broker is not the leader any more.
    transport.subscribe(partitionId, RequestType.BACKUP, new NotPartitionLeaderHandler());
    super.close();
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
      return Either.left(errorWriter.outOfDiskSpace(partitionId));
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
      return Either.left(errorWriter.mapWriteError(partitionId, written.getLeft()));
    }
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryStatusRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var backupId = requestReader.backupId();
    backupManager
        .getBackupStatus(backupId)
        .onComplete(
            (status, error) -> {
              if (error == null) {
                final BackupStatusResponse response = buildResponse(status);
                result.complete(Either.right(responseWriter.withStatus(response)));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            });
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> handleListBackupRequest(
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var pattern = requestReader.pattern();

    backupManager
        .listBackups(pattern)
        .onComplete(
            (backups, error) -> {
              if (error == null) {
                result.complete(
                    Either.right(responseWriter.withBackupList(buildBackupListResponse(backups))));
              } else {
                result.complete(
                    Either.left(
                        errorWriter
                            .errorCode(ErrorCode.INTERNAL_ERROR)
                            .errorMessage(error.getMessage())));
              }
            });
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
        .deleteBackup(backupId)
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
              partitionId,
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
              partitionId,
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
      handleQueryRangesRequest(
          final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    final var response = new BackupRangesResponse();

    backupManager
        .getBackupRangeStatus()
        .onComplete(
            (ranges, error) -> {
              if (error != null) {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
                return;
              }
              response.setRanges(
                  ranges.stream()
                      .map(
                          r ->
                              new PartitionBackupRange(
                                  partitionId,
                                  fromBackupStatus(r.first()),
                                  fromBackupStatus(r.last()),
                                  r.missingCheckpoints()))
                      .toList());
              result.complete(Either.right(responseWriter.withBackupRanges(response)));
            });

    return result;
  }

  private CheckpointInfo fromBackupStatus(final BackupStatus backupStatus) {
    if (backupStatus == null) {
      return null;
    }

    final var descriptor =
        backupStatus
            .descriptor()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Expected a backup with descriptor: " + backupStatus));
    return new CheckpointInfo(
        backupStatus.id().checkpointId(),
        descriptor.firstLogPosition().orElse(-1L),
        descriptor.checkpointPosition(),
        descriptor.checkpointType(),
        descriptor.checkpointTimestamp());
  }

  private BackupListResponse buildBackupListResponse(final Collection<BackupStatus> backups) {
    final var statuses =
        backups.stream()
            .map(
                backup ->
                    new BackupListResponse.BackupStatus(
                        backup.id().checkpointId(),
                        backup.id().partitionId(),
                        encodeStatusCode(backup.statusCode()),
                        backup.failureReason().orElse(""),
                        backup.descriptor().map(BackupDescriptor::brokerVersion).orElse(""),
                        backup.created().map(Instant::toString).orElse("")))
            .toList();
    return new BackupListResponse(statuses);
  }

  private BackupStatusResponse buildResponse(final BackupStatus status) {
    final var response =
        new BackupStatusResponse()
            .setBackupId(status.id().checkpointId())
            .setBrokerId(status.id().nodeId())
            .setPartitionId(status.id().partitionId())
            .setStatus(encodeStatusCode(status.statusCode()));
    status
        .descriptor()
        .ifPresent(
            backupDescriptor -> {
              response
                  .setCheckpointPosition(backupDescriptor.checkpointPosition())
                  .setSnapshotId(backupDescriptor.snapshotId().orElse(""))
                  .setNumberOfPartitions(backupDescriptor.numberOfPartitions())
                  .setBrokerVersion(backupDescriptor.brokerVersion());
              backupDescriptor.firstLogPosition().ifPresent(response::setFirstLogPosition);
            });
    status.failureReason().ifPresent(response::setFailureReason);
    status.created().ifPresent(instant -> response.setCreatedAt(instant.toString()));
    status.lastModified().ifPresent(instant -> response.setLastUpdated(instant.toString()));
    return response;
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> unknownRequest(
      final ErrorResponseWriter errorWriter, final BackupRequestType type) {
    errorWriter.unsupportedMessage(type, AdminRequestType.values());
    return Either.left(errorWriter);
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> backupFeatureDisabledError(
      final ErrorResponseWriter errorWriter) {
    errorWriter
        .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            "Cannot process backup requests. No backup store is configured. To use this feature, configure backup in broker configuration.");
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

  private BackupStatusCode encodeStatusCode(
      final io.camunda.zeebe.backup.api.BackupStatusCode statusCode) {
    return switch (statusCode) {
      case DOES_NOT_EXIST -> BackupStatusCode.DOES_NOT_EXIST;
      case IN_PROGRESS -> BackupStatusCode.IN_PROGRESS;
      case COMPLETED -> BackupStatusCode.COMPLETED;
      case FAILED -> BackupStatusCode.FAILED;
    };
  }
}
