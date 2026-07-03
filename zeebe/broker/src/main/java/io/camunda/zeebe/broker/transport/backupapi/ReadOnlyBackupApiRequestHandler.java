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
import io.camunda.zeebe.backup.api.BackupRangeStatus;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.ReadOnlyBackupManager;
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
 * Handles the read-only subset of the {@link RequestType#BACKUP} API — {@code QUERY_STATUS}, {@code
 * LIST} and {@code QUERY_RANGES} — served from a {@link ReadOnlyBackupManager} without touching the
 * partition's RocksDB state. All mutating request types are rejected as unsupported. This is the
 * handler used while a partition is in recovery mode; the processing-mode {@link
 * BackupApiRequestHandler} extends it to additionally serve the mutating request types.
 */
public sealed class ReadOnlyBackupApiRequestHandler
    extends AsyncApiRequestHandler<BackupApiRequestReader, BackupApiResponseWriter>
    permits BackupApiRequestHandler {

  protected final PartitionId partitionId;
  protected final boolean backupFeatureEnabled;
  private final ReadOnlyBackupManager backupManager;
  private final AtomixServerTransport transport;

  public ReadOnlyBackupApiRequestHandler(
      final ReadOnlyBackupManager backupManager,
      final PartitionId partition,
      final AtomixServerTransport transport,
      final boolean backupFeatureEnabled) {
    this("ReadOnlyBackupApi", backupManager, partition, transport, backupFeatureEnabled);
  }

  public ReadOnlyBackupApiRequestHandler(
      final String name,
      final ReadOnlyBackupManager backupManager,
      final PartitionId partition,
      final AtomixServerTransport transport,
      final boolean backupFeatureEnabled) {
    super(name, partition, BackupApiRequestReader::new, BackupApiResponseWriter::new);
    this.backupManager = backupManager;
    partitionId = partition;
    this.transport = transport;
    this.backupFeatureEnabled = backupFeatureEnabled;
  }

  @Override
  public void onActorStarted() {
    transport
        .unsubscribe(partitionId, RequestType.BACKUP)
        .thenAccept(v -> transport.subscribe(partitionId, RequestType.BACKUP, this), actor);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    return transport
        .unsubscribe(partitionId, RequestType.BACKUP)
        .thenAccept(
            ignored ->
                transport.subscribe(
                    partitionId, RequestType.BACKUP, new NotPartitionLeaderHandler()),
            actor)
        .andThen(ignored -> super.closeAsync(), actor);
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
          CompletableActorFuture.completed(unsupportedReadOnly(errorWriter, requestReader.type()));
    };
  }

  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryStatusRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupManager
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

  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleListBackupRequest(
          final BackupApiRequestReader requestReader,
          final BackupApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupManager
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

  protected ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>>
      handleQueryRangesRequest(
          final BackupApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, BackupApiResponseWriter>> result =
        new CompletableActorFuture<>();
    backupManager
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
                                  partitionId.number(),
                                  fromCheckpointInfo(r.first()),
                                  fromCheckpointInfo(r.last())))
                      .toList());
              result.complete(Either.right(responseWriter.withBackupRanges(response)));
            });
    return result;
  }

  protected Either<ErrorResponseWriter, BackupApiResponseWriter> backupFeatureDisabledError(
      final ErrorResponseWriter errorWriter) {
    errorWriter
        .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            "Cannot process backup requests. No backup store is configured. To use this feature, configure backup in broker configuration.");
    return Either.left(errorWriter);
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> unsupportedReadOnly(
      final ErrorResponseWriter errorWriter, final BackupRequestType type) {
    errorWriter
        .errorCode(ErrorCode.UNSUPPORTED_MESSAGE)
        .errorMessage(
            "Backup request of type %s is not supported while the partition is in recovery mode."
                .formatted(type));
    return Either.left(errorWriter);
  }

  protected static CheckpointInfo fromCheckpointInfo(final BackupRangeStatus.CheckpointInfo info) {
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

  protected static BackupListResponse buildBackupListResponse(
      final Collection<BackupStatus> backups) {
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

  protected static BackupStatusResponse buildResponse(final BackupStatus status) {
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

  protected static BackupStatusCode encodeStatusCode(
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
