/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueTypes;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.logging.HandoverTrace;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@NullMarked
final class CommandApiRequestHandler
    extends AsyncApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final String[] SUPPORTED_VALUE_TYPES =
      ValueTypes.userCommands().map(Enum::name).toArray(String[]::new);

  private @Nullable LogStreamWriter leadingStream;
  private boolean isDiskSpaceAvailable = true;
  private boolean processingPaused;
  private final PartitionId partitionId;
  private long rejectedPaused;
  private long rejectedNotLeader;
  private long accepted;
  private boolean acceptedSinceLeading;

  CommandApiRequestHandler(final PartitionId partitionId) {
    super("CommandApi", partitionId, CommandApiRequestReader::new, CommandApiResponseWriter::new);
    this.partitionId = partitionId;
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, CommandApiResponseWriter>> handleAsync(
      final int partitionId,
      final long requestId,
      final CommandApiRequestReader requestReader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return CompletableActorFuture.completed(
        handle(partitionId, requestId, requestReader, responseWriter, errorWriter));
  }

  public void onRecovered(final int partitionId) {
    actor.run(
        () -> {
          processingPaused = false;
          traceState("recovered");
        });
  }

  public void onPaused(final int partitionId) {
    actor.run(
        () -> {
          processingPaused = true;
          traceState("paused");
        });
  }

  public void onResumed(final int partitionId) {
    actor.run(
        () -> {
          processingPaused = false;
          traceState("resumed");
        });
  }

  /**
   * Reports how the requests since the previous state change were answered, so a handover shows how
   * many requests the old leader turned away while paused or no longer leading.
   */
  private void traceState(final String state) {
    HandoverTrace.event(
        "commandApi." + state,
        partitionId.group(),
        partitionId.number(),
        "rejectedPaused",
        rejectedPaused,
        "rejectedNotLeader",
        rejectedNotLeader,
        "accepted",
        accepted);
    rejectedPaused = 0;
    rejectedNotLeader = 0;
    accepted = 0;
  }

  private Either<ErrorResponseWriter, CommandApiResponseWriter> handle(
      final int partitionId,
      final long requestId,
      final CommandApiRequestReader requestReader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return handleExecuteCommandRequest(
        partitionId, requestId, requestReader, responseWriter, errorWriter);
  }

  private Either<ErrorResponseWriter, CommandApiResponseWriter> handleExecuteCommandRequest(
      final int partitionId,
      final long requestId,
      final CommandApiRequestReader reader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {

    if (!isDiskSpaceAvailable) {
      return Either.left(errorWriter.outOfDiskSpace(partitionId));
    }

    if (processingPaused) {
      rejectedPaused++;
      return Either.left(
          errorWriter.partitionUnavailable(
              String.format("Processing paused for partition '%s'", partitionId)));
    }

    final var command = reader.getMessageDecoder();
    final var logStreamWriter = leadingStream;

    final var valueType = command.valueType();
    final var intent = Intent.fromProtocolValue(valueType, command.intent());
    final var value = reader.value();
    final long operationReference = command.operationReference();
    final var metadata = reader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.recordType(RecordType.COMMAND);
    metadata.intent(intent);
    metadata.valueType(valueType);
    metadata.operationReference(operationReference);

    if (logStreamWriter == null) {
      rejectedNotLeader++;
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    if (value == null) {
      errorWriter.unsupportedMessage(valueType.name(), SUPPORTED_VALUE_TYPES);
      return Either.left(errorWriter);
    }

    accepted++;
    if (!acceptedSinceLeading) {
      acceptedSinceLeading = true;
      HandoverTrace.event(
          "commandApi.firstAccepted", this.partitionId.group(), this.partitionId.number());
    }
    try {
      return writeCommand(command.key(), metadata, value, logStreamWriter, errorWriter, partitionId)
          .map(b -> responseWriter)
          .mapLeft(failure -> errorWriter);

    } catch (final Exception error) {
      final String errorMessage =
          "Failed to write client request to partition '%d', %s".formatted(partitionId, error);
      LOG.error(errorMessage);
      return Either.left(errorWriter.internalError(errorMessage));
    }
  }

  private Either<ErrorResponseWriter, Boolean> writeCommand(
      final long key,
      final RecordMetadata metadata,
      final UnifiedRecordValue value,
      final LogStreamWriter logStreamWriter,
      final ErrorResponseWriter errorWriter,
      final int partitionId) {
    final LogAppendEntry appendEntry;
    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      appendEntry = LogAppendEntry.of(key, metadata, value);
    } else {
      appendEntry = LogAppendEntry.of(metadata, value);
    }

    if (logStreamWriter.canWriteEvents(1, appendEntry.getLength())) {
      return logStreamWriter
          .tryWrite(WriteContext.userCommand(metadata.getIntent()), appendEntry)
          .map(ignore -> true)
          .mapLeft(error -> errorWriter.mapWriteError(partitionId, error));
    } else {
      return Either.left(
          errorWriter
              .errorCode(ErrorCode.MALFORMED_REQUEST)
              .errorMessage("Request size is above configured maxMessageSize."));
    }
  }

  void addPartition(final int partitionId, final LogStreamWriter logStreamWriter) {
    actor.submit(
        () -> {
          leadingStream = logStreamWriter;
          acceptedSinceLeading = false;
          traceState("leading");
        });
  }

  void removePartition(final int partitionId) {
    actor.submit(
        () -> {
          leadingStream = null;
          traceState("notLeading");
        });
  }

  void onDiskSpaceNotAvailable() {
    actor.submit(
        () -> {
          isDiskSpaceAvailable = false;
          LOG.debug("Broker is out of disk space. All client requests will be rejected");
        });
  }

  void onDiskSpaceAvailable() {
    actor.submit(() -> isDiskSpaceAvailable = true);
  }
}
