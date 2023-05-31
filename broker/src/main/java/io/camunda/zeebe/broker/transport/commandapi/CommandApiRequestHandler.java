/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.broker.transport.backpressure.BackpressureMetrics;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

final class CommandApiRequestHandler
    extends AsyncApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final Int2ObjectHashMap<LogStreamWriter> leadingStreams = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<RequestLimiter<Intent>> partitionLimiters =
      new Int2ObjectHashMap<>();
  private final BackpressureMetrics metrics = new BackpressureMetrics();
  private boolean isDiskSpaceAvailable = true;

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

    final var command = reader.getMessageDecoder();
    final var logStreamWriter = leadingStreams.get(partitionId);
    final var limiter = partitionLimiters.get(partitionId);

    final var valueType = command.valueType();
    final var intent = Intent.fromProtocolValue(valueType, command.intent());
    final var value = reader.value();
    final var metadata = reader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.recordType(RecordType.COMMAND);
    metadata.intent(intent);
    metadata.valueType(valueType);

    if (logStreamWriter == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    if (value == null) {
      errorWriter.unsupportedMessage(
          valueType.name(), CommandApiRequestReader.RECORDS_BY_TYPE.keySet().toArray());
      return Either.left(errorWriter);
    }

    metrics.receivedRequest(partitionId);
    if (!limiter.tryAcquire(partitionId, requestId, intent)) {
      metrics.dropped(partitionId);
      LOG.trace(
          "Partition-{} receiving too many requests. Current limit {} inflight {}, dropping request {} from gateway",
          partitionId,
          limiter.getLimit(),
          limiter.getInflightCount(),
          requestId);
      errorWriter.resourceExhausted();
      return Either.left(errorWriter);
    }

    try {
      return writeCommand(command.key(), metadata, value, logStreamWriter)
          .map(b -> responseWriter)
          .mapLeft(
              failure ->
                  handleErrorOnWrite(
                      partitionId, requestId, errorWriter, limiter, intent, failure));

    } catch (final Exception ex) {
      return Either.left(
          handleErrorOnWrite(partitionId, requestId, errorWriter, limiter, intent, ex.toString()));
    }
  }

  private static ErrorResponseWriter handleErrorOnWrite(
      final int partitionId,
      final long requestId,
      final ErrorResponseWriter errorWriter,
      final RequestLimiter<Intent> limiter,
      final Intent intent,
      final String failure) {
    limiter.onIgnore(partitionId, requestId);
    LOG.error("Unexpected error on writing {} command {}", intent, failure);
    errorWriter.internalError("Failed writing request: %s", failure);
    return errorWriter;
  }

  private Either<String, Boolean> writeCommand(
      final long key,
      final RecordMetadata metadata,
      final UnifiedRecordValue value,
      final LogStreamWriter logStreamWriter) {
    final LogAppendEntry appendEntry;
    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      appendEntry = LogAppendEntry.of(key, metadata, value);
    } else {
      appendEntry = LogAppendEntry.of(metadata, value);
    }

    if (logStreamWriter.canWriteEvents(1, appendEntry.getLength())) {
      return logStreamWriter
          .tryWrite(appendEntry)
          .map(ignore -> true)
          .mapLeft(error -> "Failed to write request to logstream");
    } else {
      return Either.left("Request size is above configured maxMessageSize.");
    }
  }

  void addPartition(
      final int partitionId,
      final LogStreamWriter logStreamWriter,
      final RequestLimiter<Intent> limiter) {
    actor.submit(
        () -> {
          leadingStreams.put(partitionId, logStreamWriter);
          partitionLimiters.put(partitionId, limiter);
        });
  }

  void removePartition(final int partitionId) {
    actor.submit(
        () -> {
          leadingStreams.remove(partitionId);
          partitionLimiters.remove(partitionId);
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
