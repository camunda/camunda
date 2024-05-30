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
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;

final class GetApiRequestHandler
    extends AsyncApiRequestHandler<GetApiRequestReader, GetApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private boolean isDiskSpaceAvailable = true;

  GetApiRequestHandler() {
    super(GetApiRequestReader::new, GetApiResponseWriter::new);
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, GetApiResponseWriter>> handleAsync(
      final int partitionId,
      final long requestId,
      final GetApiRequestReader requestReader,
      final GetApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return CompletableActorFuture.completed(
        handle(partitionId, requestId, requestReader, responseWriter, errorWriter));
  }

  private Either<ErrorResponseWriter, GetApiResponseWriter> handle(
      final int partitionId,
      final long requestId,
      final GetApiRequestReader requestReader,
      final GetApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {

    if (!isDiskSpaceAvailable) {
      return Either.left(errorWriter.outOfDiskSpace(partitionId));
    }

    final var request = requestReader.getMessageDecoder();

    final var valueType = request.valueType();
    final var metadata = requestReader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.valueType(valueType);

    try {
      return getEntity(request.key(), metadata, errorWriter, partitionId)
          .map(b -> responseWriter)
          .mapLeft(failure -> errorWriter);

    } catch (final Exception error) {
      final String errorMessage =
          "Failed to write client request to partition '%d', %s".formatted(partitionId, error);
      LOG.error(errorMessage);
      return Either.left(errorWriter.internalError(errorMessage));
    }
  }

  private Either<ErrorResponseWriter, Boolean> getEntity(
      final long key,
      final RecordMetadata metadata,
      final ErrorResponseWriter errorWriter,
      final int partitionId) {
    if (key != ExecuteGetRequestDecoder.keyNullValue()) {
      return Either.left(
          errorWriter.errorCode(ErrorCode.MALFORMED_REQUEST).errorMessage("No key provided"));
    }

    // todo: retrieve entity from state
    // todo: respond found entity

    return Either.right(true);
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
