/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.protocol.impl.encoding.BackupListResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.PartitionBackupRange;
import io.camunda.zeebe.protocol.impl.encoding.BackupStatusResponse;
import io.camunda.zeebe.protocol.management.BackupRequestType;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.util.Collection;

/**
 * Handles {@link RequestType#BACKUP} requests while a partition is in recovery mode. Only the
 * read-only subset of the backup API is supported: {@code QUERY_STATUS}, {@code LIST} and {@code
 * QUERY_RANGES}, all served by the {@link RecoveryBackupService} without touching the partition's
 * RocksDB state. All other request types are rejected as unsupported during recovery.
 */
public final class RecoveryBackupApiRequestHandler
    extends AsyncApiRequestHandler<BackupApiRequestReader, BackupApiResponseWriter> {

  private final AtomixServerTransport transport;
  private final RecoveryBackupService backupService;
  private final PartitionId partition;
  private final int partitionId;
  private final boolean backupFeatureEnabled;

  public RecoveryBackupApiRequestHandler(
      final AtomixServerTransport transport,
      final RecoveryBackupService backupService,
      final PartitionId partition,
      final boolean backupFeatureEnabled) {
    super(BackupApiRequestReader::new, BackupApiResponseWriter::new);
    this.transport = transport;
    this.backupService = backupService;
    this.partition = partition;
    partitionId = partition.number();
    this.backupFeatureEnabled = backupFeatureEnabled;
    transport.unsubscribe(partition, RequestType.BACKUP);
    transport.subscribe(partition, RequestType.BACKUP, this);
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
      case QUERY_STATUS -> handleQueryStatusRequest(requestReader, responseWriter, errorWriter);
      case LIST -> handleListBackupRequest(requestReader, responseWriter, errorWriter);
      case QUERY_RANGES -> handleQueryRangesRequest(responseWriter, errorWriter);
      default ->
          CompletableActorFuture.completed(
              unsupportedDuringRecovery(errorWriter, requestReader.type()));
    };
  }

  @Override
  public String getName() {
    return "RecoveryBackupApiRequestHandler";
  }

  @Override
  public void close() {
    transport.unsubscribe(partition, RequestType.BACKUP);
    super.close();
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryStatusRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupService
        .getBackupStatus(requestReader.backupId())
        .onComplete(
            (status, error) -> {
              if (error == null) {
                result.complete(Either.right(responseWriter.withStatus(buildResponse(status))));
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
    backupService
        .listBackups(requestReader.pattern())
        .onComplete(
            (backups, error) -> {
              if (error == null) {
                result.complete(
                    Either.right(responseWriter.withBackupList(buildBackupListResponse(backups))));
              } else {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
              }
            });
    return result;
  }

  private ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryRangesRequest(
          final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupService
        .getBackupRangeStatus()
        .onComplete(
            (ranges, error) -> {
              if (error != null) {
                errorWriter.errorCode(ErrorCode.INTERNAL_ERROR).errorMessage(error.getMessage());
                result.complete(Either.left(errorWriter));
                return;
              }
              final var response = new BackupRangesResponse();
              response.setRanges(
                  ranges.stream()
                      .map(
                          r ->
                              new PartitionBackupRange(
                                  partitionId,
                                  fromCheckpointInfo(r.first()),
                                  fromCheckpointInfo(r.last())))
                      .toList());
              result.complete(Either.right(responseWriter.withBackupRanges(response)));
            });
    return result;
  }

  private CheckpointInfo fromCheckpointInfo(final BackupRangeStatus.CheckpointInfo info) {
    if (info == null) {
      return null;
    }
    return new CheckpointInfo(
        info.checkpointId(),
        info.firstLogPosition(),
        info.checkpointPosition(),
        info.checkpointType(),
        Instant.ofEpochMilli(info.checkpointTimestamp()));
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
            .setBrokerId(status.id().nodeId(), status.id().zone())
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

  private Either<ErrorResponseWriter, BackupApiResponseWriter> unsupportedDuringRecovery(
      final ErrorResponseWriter errorWriter, final BackupRequestType type) {
    errorWriter
        .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            "Backup request of type %s is not supported while the partition is in recovery mode."
                .formatted(type));
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

  private BackupStatusCode encodeStatusCode(
      final io.camunda.zeebe.backup.api.BackupStatusCode statusCode) {
    return switch (statusCode) {
      case DOES_NOT_EXIST -> BackupStatusCode.DOES_NOT_EXIST;
      case IN_PROGRESS -> BackupStatusCode.IN_PROGRESS;
      case COMPLETED -> BackupStatusCode.COMPLETED;
      case FAILED -> BackupStatusCode.FAILED;
      case DELETED -> BackupStatusCode.DELETED;
    };
  }
}
