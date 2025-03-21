/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

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
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import java.util.HashMap;
import java.util.Map;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

final class CommandApiRequestHandler
    extends AsyncApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final Int2ObjectHashMap<LogStreamWriter> leadingStreams = new Int2ObjectHashMap<>();
  private boolean isDiskSpaceAvailable = true;
  private final Map<Integer, Boolean> processingPaused = new HashMap<>();

  CommandApiRequestHandler() {
    super(CommandApiRequestReader::new, CommandApiResponseWriter::new);
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
    actor.run(() -> processingPaused.put(partitionId, false));
  }

  public void onPaused(final int partitionId) {
    actor.run(() -> processingPaused.put(partitionId, true));
  }

  public void onResumed(final int partitionId) {
    actor.run(() -> processingPaused.put(partitionId, false));
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

    if (processingPaused.getOrDefault(partitionId, false)) {
      return Either.left(
          errorWriter.partitionUnavailable(
              String.format("Processing paused for partition '%s'", partitionId)));
    }

    final var command = reader.getMessageDecoder();
    final var logStreamWriter = leadingStreams.get(partitionId);

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
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    if (value == null) {
      errorWriter.unsupportedMessage(
          valueType.name(), CommandApiRequestReader.RECORDS_BY_TYPE.keySet().toArray());
      return Either.left(errorWriter);
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
              .errorCode(ErrorCode.MAX_MESSAGE_SIZE_EXCEEDED)
              .errorMessage("Request size is above configured maxMessageSize."));
    }
  }

  void addPartition(final int partitionId, final LogStreamWriter logStreamWriter) {
    actor.submit(() -> leadingStreams.put(partitionId, logStreamWriter));
  }

  void removePartition(final int partitionId) {
    actor.submit(() -> leadingStreams.remove(partitionId));
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
