/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.backupapi;

import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.ApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.AdminRequestType;
import io.camunda.zeebe.protocol.record.BackupRequestType;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;

/**
 * Request handler to handle commands and queries related to the backup ({@link RequestType#BACKUP})
 */
public final class BackupApiRequestHandler
    extends ApiRequestHandler<BackupApiRequestReader, BackupApiResponseWriter>
    implements DiskSpaceUsageListener {
  private boolean isDiskSpaceAvailable = true;
  private final LogStreamRecordWriter logStreamRecordWriter;
  private final AtomixServerTransport transport;
  private final int partitionId;

  public BackupApiRequestHandler(
      final AtomixServerTransport transport,
      final LogStreamRecordWriter logStreamRecordWriter,
      final int partitionId) {
    super(new BackupApiRequestReader(), new BackupApiResponseWriter());
    this.logStreamRecordWriter = logStreamRecordWriter;
    this.transport = transport;
    this.partitionId = partitionId;
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
  protected Either<ErrorResponseWriter, BackupApiResponseWriter> handle(
      final int partitionId,
      final long requestId,
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {

    if (requestReader.type() == BackupRequestType.TAKE_BACKUP) {
      if (!isDiskSpaceAvailable) {
        return Either.left(errorWriter.outOfDiskSpace(partitionId));
      }
      return handleTakeBackupRequest(requestReader, responseWriter, errorWriter);
    }

    return unknownRequest(errorWriter, requestReader.getMessageDecoder().type());
  }

  private Either<ErrorResponseWriter, BackupApiResponseWriter> handleTakeBackupRequest(
      final BackupApiRequestReader requestReader,
      final BackupApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final RecordMetadata metadata =
        new RecordMetadata()
            .recordType(RecordType.COMMAND)
            .valueType(ValueType.CHECKPOINT)
            .intent(CheckpointIntent.CREATE);
    final CheckpointRecord checkpointRecord =
        new CheckpointRecord().setCheckpointId(requestReader.backupId());

    final var written =
        logStreamRecordWriter.metadataWriter(metadata).valueWriter(checkpointRecord).tryWrite();

    if (written > 0) {
      // Response will be sent by the processor
      return Either.right(responseWriter.noResponse());
    } else {
      return Either.left(errorWriter.internalError("Failed to write command to logstream."));
    }
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
