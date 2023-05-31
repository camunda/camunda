/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
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
  private final int partitionId;
  private final boolean backupFeatureEnabled;

  public BackupApiRequestHandler(
      final AtomixServerTransport transport,
      final LogStreamWriter logStreamWriter,
      final BackupManager backupManager,
      final int partitionId,
      final boolean backupFeatureEnabled) {
    super(BackupApiRequestReader::new, BackupApiResponseWriter::new);
    this.logStreamWriter = logStreamWriter;
    this.transport = transport;
    this.backupManager = backupManager;
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
      case TAKE_BACKUP -> CompletableActorFuture.completed(
          handleTakeBackupRequest(
              requestStreamId, requestId, requestReader, responseWriter, errorWriter));
      case QUERY_STATUS -> handleQueryStatusRequest(requestReader, responseWriter, errorWriter);
      case LIST -> handleListBackupRequest(responseWriter, errorWriter);
      case DELETE -> handleDeleteBackupRequest(requestReader, responseWriter, errorWriter);
      default -> CompletableActorFuture.completed(
          unknownRequest(errorWriter, requestReader.getMessageDecoder().type()));
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
    final var checkpointRecord = new CheckpointRecord().setCheckpointId(requestReader.backupId());
    final var written = logStreamWriter.tryWrite(LogAppendEntry.of(metadata, checkpointRecord));

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
      final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();

    backupManager
        .listBackups()
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
            backupDescriptor ->
                response
                    .setCheckpointPosition(backupDescriptor.checkpointPosition())
                    .setSnapshotId(backupDescriptor.snapshotId().orElse(""))
                    .setNumberOfPartitions(backupDescriptor.numberOfPartitions())
                    .setBrokerVersion(backupDescriptor.brokerVersion()));
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
