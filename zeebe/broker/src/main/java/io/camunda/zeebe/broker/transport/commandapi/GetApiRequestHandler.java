/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.PartitionGetAccess;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import org.slf4j.Logger;

public final class GetApiRequestHandler
    extends AsyncApiRequestHandler<GetApiRequestReader, GetApiResponseWriter> {
  private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

  private final AtomixServerTransport transport;
  private final PartitionGetAccess getAccess;
  private final RaftPartition raftPartition;

  public GetApiRequestHandler(
      final AtomixServerTransport transport,
      final PartitionGetAccess getAccess,
      final RaftPartition raftPartition) {
    super(GetApiRequestReader::new, GetApiResponseWriter::new);
    this.transport = transport;
    this.getAccess = getAccess;
    this.raftPartition = raftPartition;
  }

  @Override
  protected void onActorStarting() {
    transport.subscribe(raftPartition.id().id(), RequestType.GET, this);
  }

  @Override
  protected void onActorClosing() {
    transport.unsubscribe(raftPartition.id().id(), RequestType.GET);
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

    final var request = requestReader.getMessageDecoder();
    final var valueType = request.valueType();
    final var metadata = requestReader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.valueType(valueType);

    try {
      return getEntity(request.key(), metadata, errorWriter, partitionId, responseWriter)
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
      final int partitionId,
      final GetApiResponseWriter responseWriter) {
    if (key == ExecuteGetRequestDecoder.keyNullValue()) {
      return Either.left(
          errorWriter.errorCode(ErrorCode.MALFORMED_REQUEST).errorMessage("No key provided"));
    }

    // todo: retrieve entity from state
    // todo: respond found entity

    return Either.left(
        errorWriter
            .errorCode(ErrorCode.INTERNAL_ERROR)
            .errorMessage("you need to implement regular response writing still"));
  }
}
