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
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.ExecuteGetRequestDecoder;
import io.camunda.zeebe.protocol.record.ResponseType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
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

    final var request = requestReader.getMessageDecoder();
    final var valueType = request.valueType();
    final var metadata = requestReader.metadata();

    metadata.requestId(requestId);
    metadata.requestStreamId(partitionId);
    metadata.valueType(valueType);

    final long key = request.key();
    if (key == ExecuteGetRequestDecoder.keyNullValue()) {
      return CompletableActorFuture.completed(
          Either.left(
              errorWriter.errorCode(ErrorCode.MALFORMED_REQUEST).errorMessage("No key provided")));
    }

    final var result =
        new CompletableActorFuture<Either<ErrorResponseWriter, GetApiResponseWriter>>();
    getAccess
        .getEntity(key, metadata)
        .onComplete(
            (entity, throwable) -> {
              if (throwable != null) {
                // todo: map throwable to error code
                // todo: don't return a left, but return a right with rejection message set
                result.complete(
                    Either.left(
                        errorWriter
                            .errorCode(ErrorCode.INTERNAL_ERROR)
                            .errorMessage(throwable.getMessage())));
              } else {
                responseWriter
                    .partitionId(partitionId)
                    .key(key)
                    .responseType(ResponseType.OK)
                    .valueType(ValueType.USER_TASK)
                    .intent(entity.getIntent())
                    // todo: see if there's a way without copying the buffer
                    .value(BufferUtil.createCopy(entity.getValue()));
                result.complete(Either.right(responseWriter));
              }
            });
    return result;
  }
}
