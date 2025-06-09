/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.SnapshotChunkResponse;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SnapshotApiRequestHandler
    extends AsyncApiRequestHandler<SnapshotApiRequestReader, SnapshotApiResponseWriter> {

  private final ConcurrentMap<Integer, SnapshotTransferService> transferServices =
      new ConcurrentHashMap<>();
  private final AtomixServerTransport serverTransport;

  protected SnapshotApiRequestHandler(final AtomixServerTransport serverTransport) {
    super(SnapshotApiRequestReader::new, SnapshotApiResponseWriter::new);
    this.serverTransport = serverTransport;
  }

  public void addTransferService(
      final int partitionId, final SnapshotTransferService transferService) {
    serverTransport.subscribe(partitionId, RequestType.SNAPSHOT, this);
    transferServices.put(partitionId, transferService);
  }

  public void removeTransferService(final int partitionId) {
    serverTransport.unsubscribe(partitionId, RequestType.SNAPSHOT);
    final var service = transferServices.remove(partitionId);
    service.closeAsync();
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleAsync(
      final int partitionId,
      final long requestId,
      final SnapshotApiRequestReader requestReader,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final var service = transferServices.get(partitionId);
    if (service == null) {
      return CompletableActorFuture.completed(
          Either.left(errorWriter.partitionUnavailable(partitionId)));
    } else {
      final var request = requestReader.getRequest();
      return switch (request) {
        case final GetSnapshotChunk snapshotChunkRequest ->
            handleGet(snapshotChunkRequest, partitionId, responseWriter, service);
        case final DeleteSnapshotForBootstrapRequest deleteRequest ->
            handleDelete(partitionId, responseWriter, errorWriter, service);
      };
    }
  }

  private ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleDelete(
      final int partitionId,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter,
      final SnapshotTransferService service) {
    return service
        .deleteSnapshots(partitionId)
        .andThen(
            (response, error) -> {
              final Either<ErrorResponseWriter, SnapshotApiResponseWriter> result;
              if (error != null) {
                result = Either.left(errorWriter.internalError(error.getMessage()));
              } else {
                responseWriter.setResponse(new DeleteSnapshotForBootstrapResponse(partitionId));
                result = Either.right(responseWriter);
              }
              return CompletableActorFuture.completed(result);
            },
            actor);
  }

  private ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleGet(
      final GetSnapshotChunk request,
      final int partitionId,
      final SnapshotApiResponseWriter responseWriter,
      final SnapshotTransferService service) {
    if (request.lastChunkName().isPresent() && request.snapshotId().isPresent()) {
      return service
          .getNextChunk(
              partitionId,
              request.snapshotId().get(),
              request.lastChunkName().get(),
              request.transferId())
          .thenApply(
              chunk -> {
                responseWriter.setResponse(
                    new SnapshotChunkResponse(request.transferId(), Optional.ofNullable(chunk)));
                return Either.right(responseWriter);
              });
    } else {
      return service
          .getLatestSnapshot(partitionId, request.transferId())
          .thenApply(
              chunk -> {
                responseWriter.setResponse(
                    new SnapshotChunkResponse(request.transferId(), Optional.ofNullable(chunk)));
                return Either.right(responseWriter);
              });
    }
  }

  @Override
  public void close() {
    transferServices.forEach((partitionId, service) -> removeTransferService(partitionId));
    super.close();
  }
}
