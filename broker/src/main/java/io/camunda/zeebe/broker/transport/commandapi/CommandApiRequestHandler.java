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
import io.camunda.zeebe.logstreams.impl.log.DispatcherClaimException;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.Either;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

final class CommandApiRequestHandler
    extends AsyncApiRequestHandler<CommandApiRequestReader, CommandApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final Int2ObjectHashMap<LogStreamRecordWriter> leadingStreams = new Int2ObjectHashMap<>();
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
    return handle(partitionId, requestId, requestReader, responseWriter, errorWriter);
  }

  private ActorFuture<Either<ErrorResponseWriter, CommandApiResponseWriter>> handle(
      final int partitionId,
      final long requestId,
      final CommandApiRequestReader requestReader,
      final CommandApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return handleExecuteCommandRequest(
        partitionId, requestId, requestReader, responseWriter, errorWriter);
  }

  private ActorFuture<Either<ErrorResponseWriter, CommandApiResponseWriter>>
      handleExecuteCommandRequest(
          final int partitionId,
          final long requestId,
          final CommandApiRequestReader reader,
          final CommandApiResponseWriter responseWriter,
          final ErrorResponseWriter errorWriter) {
    final ActorFuture<Either<ErrorResponseWriter, CommandApiResponseWriter>> result =
        actor.createFuture();
    if (!isDiskSpaceAvailable) {
      result.complete(Either.left(errorWriter.outOfDiskSpace(partitionId)));
      return result;
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
      result.complete(Either.left(errorWriter));
      return result;
    }

    if (event == null) {
      errorWriter.unsupportedMessage(
          eventType.name(), CommandApiRequestReader.RECORDS_BY_TYPE.keySet().toArray());
      result.complete(Either.left(errorWriter));
      return result;
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
      result.complete(Either.left(errorWriter));
      return result;
    }

    // theoretically, if we switch phases here, we would not release the limiter since the callback
    // will not be called. this should be fine as we only close here when the broker is shutting
    // down, but this is definitely
    final var writeResult = writeCommand(command.key(), metadata, event, logStreamWriter);
    actor.runOnCompletion(
        writeResult,
        (position, error) -> {
          if (error == null) {
            result.complete(Either.right(responseWriter));
          } else {
            limiter.onIgnore(partitionId, requestId);
            if (error instanceof DispatcherClaimException) {
              result.complete(Either.right(responseWriter));
            } else {
              LOG.error("Unexpected error on writing {} command", intent, error);
              errorWriter.internalError("Failed writing response: %s", error);
              result.complete(Either.left(errorWriter));
            }
          }
        });

    return result;
  }

  private ActorFuture<Long> writeCommand(
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

    return logStreamWriter.metadataWriter(eventMetadata).valueWriter(event).tryWrite();
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
