/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.primitive.partition.PartitionId;
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
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

final class CommandApiRequestHandler
    extends AsyncApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;
  private static final String[] SUPPORTED_VALUE_TYPES =
      ValueTypes.userCommands().map(Enum::name).toArray(String[]::new);

  private final HashMap<PartitionId, LogStreamWriter> leadingStreams = new HashMap();
  private boolean isDiskSpaceAvailable = true;
  private final Map<PartitionId, Boolean> processingPaused = new HashMap<>();

  CommandApiRequestHandler() {
    super(CommandApiRequestReader::new, CommandApiResponseWriter::new);
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, CommandApiResponseWriter>> handleAsync(
      final PartitionId partitionId,
      final long requestId,
      final CommandApiRequestReader requestReader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return CompletableActorFuture.completed(
        handle(partitionId, requestId, requestReader, responseWriter, errorWriter));
  }

  public void onRecovered(final PartitionId partitionId) {
    actor.run(() -> processingPaused.put(partitionId, false));
  }

  public void onPaused(final PartitionId partitionId) {
    actor.run(() -> processingPaused.put(partitionId, true));
  }

  public void onResumed(final PartitionId partitionId) {
    actor.run(() -> processingPaused.put(partitionId, false));
  }

  private Either<ErrorResponseWriter, CommandApiResponseWriter> handle(
      final PartitionId partitionId,
      final long requestId,
      final CommandApiRequestReader requestReader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return handleExecuteCommandRequest(
        partitionId, requestId, requestReader, responseWriter, errorWriter);
  }

  private Either<ErrorResponseWriter, CommandApiResponseWriter> handleExecuteCommandRequest(
      final PartitionId partitionId,
      final long requestId,
      final CommandApiRequestReader reader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {

    if (!isDiskSpaceAvailable) {
      return Either.left(errorWriter.outOfDiskSpace(partitionId.id()));
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
    metadata.requestStreamId(partitionId.id());
    metadata.recordType(RecordType.COMMAND);
    metadata.intent(intent);
    metadata.valueType(valueType);
    metadata.operationReference(operationReference);

    if (logStreamWriter == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    if (value == null) {
      errorWriter.unsupportedMessage(valueType.name(), SUPPORTED_VALUE_TYPES);
      return Either.left(errorWriter);
    }

    try {
      return writeCommand(
              command.key(), metadata, value, logStreamWriter, errorWriter, partitionId.id())
          .map(b -> responseWriter)
          .mapLeft(failure -> errorWriter);

    } catch (final Exception error) {
      final String errorMessage =
          "Failed to write client request to partition '%s', %s".formatted(partitionId, error);
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

  void addPartition(final PartitionId partitionId, final LogStreamWriter logStreamWriter) {
    actor.submit(() -> leadingStreams.put(partitionId, logStreamWriter));
  }

  void removePartition(final PartitionId partitionId) {
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
