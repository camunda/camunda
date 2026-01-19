/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.SnapshotChunkResponse;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.GetScaleUpProgress;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotSenderService;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class SnapshotApiRequestHandler
    extends AsyncApiRequestHandler<SnapshotApiRequestReader, SnapshotApiResponseWriter> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotApiRequestHandler.class);
  private final ConcurrentMap<Integer, SnapshotSenderService> transferServices =
      new ConcurrentHashMap<>();
  private final ServerTransport serverTransport;
  private final BrokerClient brokerClient;

  public SnapshotApiRequestHandler(
      final ServerTransport serverTransport, final BrokerClient brokerClient) {
    super(SnapshotApiRequestReader::new, SnapshotApiResponseWriter::new);
    this.serverTransport = serverTransport;
    this.brokerClient = brokerClient;
  }

  public void addTransferService(
      final int partitionId, final SnapshotSenderService transferService) {
    serverTransport.subscribe(partitionId, RequestType.SNAPSHOT, this);
    transferServices.put(partitionId, transferService);
    LOG.debug("Added SnapshotTransferService for partition {}.", partitionId);
  }

  public void removeTransferService(final int partitionId) {
    serverTransport.unsubscribe(partitionId, RequestType.SNAPSHOT);
    final var service = transferServices.remove(partitionId);
    LOG.debug("Removed SnapshotTransferService for partition {}.", partitionId);
    AsyncClosable.closeHelper(service);
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleAsync(
      final PartitionId partitionId,
      final long requestId,
      final SnapshotApiRequestReader requestReader,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final var service = transferServices.get(partitionId);
    if (service == null) {
      return CompletableActorFuture.completed(
          Either.left(errorWriter.partitionUnavailable(partitionId.id())));
    } else {
      final var request = requestReader.getRequest();
      return switch (request) {
        // TODO: Use full partition id, including the group
        case final GetSnapshotChunk snapshotChunkRequest ->
            handleGet(snapshotChunkRequest, partitionId.id(), responseWriter, errorWriter, service);
        case final DeleteSnapshotForBootstrapRequest deleteRequest ->
            handleDelete(partitionId.id(), responseWriter, errorWriter, service);
      };
    }
  }

  private ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleDelete(
      final int partitionId,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter,
      final SnapshotSenderService service) {
    return service
        .deleteSnapshots(partitionId)
        .andThen(
            (response, error) -> {
              final Either<ErrorResponseWriter, SnapshotApiResponseWriter> result;
              if (error != null) {
                LOG.warn("Failed to delete snapshots for partition {}", partitionId, error);
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
      final ErrorResponseWriter errorWriter,
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
      LOG.atLevel(Level.DEBUG)
          .addKeyValue("transferId", request.transferId())
          .log("Received request to get the latest snapshot for partition {}", partitionId);
      return getLastProcessedPositionRequired(request.transferId())
          .andThen(
              lastProcessedPosition -> {
                LOG.atLevel(Level.DEBUG)
                    .addKeyValue("transferId", request.transferId())
                    .log("Last processed position is {}", lastProcessedPosition);
                return service.getLatestSnapshot(
                    partitionId, lastProcessedPosition, request.transferId());
              },
              actor)
          .andThen(
              (chunk, error) -> {
                if (error != null) {
                  LOG.error(
                      "Failed to get the latest snapshot for partition {}: {}",
                      partitionId,
                      error.getMessage(),
                      error);
                  return CompletableActorFuture.completed(
                      Either.left(errorWriter.internalError(error.getMessage())));
                }
                responseWriter.setResponse(
                    new SnapshotChunkResponse(request.transferId(), Optional.ofNullable(chunk)));
                return CompletableActorFuture.completed(Either.right(responseWriter));
              },
              actor);
    }
  }

  @Override
  public void close() {
    LOG.debug(
        "Closing SnapshotApiRequestHandler. Removing transfer services. Registered partitions {}",
        transferServices.keySet());
    transferServices.forEach((partitionId, service) -> removeTransferService(partitionId));
    super.close();
  }

  private ActorFuture<Long> getLastProcessedPositionRequired(final UUID transferId) {
    final ActorFuture<Long> lastProcessedPosition = actor.createFuture();
    brokerClient
        .sendRequestWithRetry(new GetScaleUpProgress())
        .thenApply(
            r -> {
              LOG.atLevel(Level.DEBUG)
                  .addKeyValue("transferId", transferId)
                  .log("Received response from broker {}", r.getResponse());
              return r.getResponse().getScalingPosition();
            })
        .whenComplete(lastProcessedPosition);
    return lastProcessedPosition;
  }
}
