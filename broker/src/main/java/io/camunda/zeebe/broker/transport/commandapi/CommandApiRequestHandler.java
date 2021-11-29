/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.transport.ApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.broker.transport.backpressure.BackpressureMetrics;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.util.Either;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

final class CommandApiRequestHandler
    extends ApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final Int2ObjectHashMap<LogStreamRecordWriter> leadingStreams = new Int2ObjectHashMap<>();
  private final Int2ObjectHashMap<RequestLimiter<Intent>> partitionLimiters =
      new Int2ObjectHashMap<>();
  private final BackpressureMetrics metrics = new BackpressureMetrics();
  private boolean isDiskSpaceAvailable = true;

  CommandApiRequestHandler() {
    super(new CommandApiRequestReader(), new CommandApiResponseWriter());
  }

  @Override
  protected Either<ErrorResponseWriter, CommandApiResponseWriter> handle(
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
      errorWriter.resourceExhausted(
          String.format(
              "Cannot accept requests for partition %d. Broker is out of disk space", partitionId));
      return Either.left(errorWriter);
    }

    final var command = reader.getMessageDecoder();
    final var logStreamWriter = leadingStreams.get(partitionId);
    final var limiter = partitionLimiters.get(partitionId);

    final var eventType = command.valueType();
    final var intent = Intent.fromProtocolValue(eventType, command.intent());
    final var event = reader.event();
    final var metadata = reader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.recordType(RecordType.COMMAND);
    metadata.intent(intent);
    metadata.valueType(eventType);

    if (logStreamWriter == null) {
      errorWriter.partitionLeaderMismatch(partitionId);
      return Either.left(errorWriter);
    }

    if (event == null) {
      errorWriter.unsupportedMessage(
          eventType.name(), CommandApiRequestReader.RECORDS_BY_TYPE.keySet().toArray());
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

    boolean written = false;
    try {
      written = writeCommand(command.key(), metadata, event, logStreamWriter);
      return Either.right(responseWriter);
    } catch (final Exception ex) {
      LOG.error("Unexpected error on writing {} command", intent, ex);
      errorWriter.internalError("Failed writing response: %s", ex);
      return Either.left(errorWriter);
    } finally {
      if (!written) {
        limiter.onIgnore(partitionId, requestId);
      }
    }
  }

  private boolean writeCommand(
      final long key,
      final RecordMetadata eventMetadata,
      final UnpackedObject event,
      final LogStreamRecordWriter logStreamWriter) {
    logStreamWriter.reset();

    if (key != ExecuteCommandRequestDecoder.keyNullValue()) {
      logStreamWriter.key(key);
    } else {
      logStreamWriter.keyNull();
    }

    final long eventPosition =
        logStreamWriter.metadataWriter(eventMetadata).valueWriter(event).tryWrite();

    return eventPosition >= 0;
  }

  void addPartition(
      final int partitionId,
      final LogStreamRecordWriter logStreamWriter,
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
