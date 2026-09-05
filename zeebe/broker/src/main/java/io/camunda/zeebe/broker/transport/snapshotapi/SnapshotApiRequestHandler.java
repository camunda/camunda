/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.DeleteSnapshotForBootstrapRequest;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotRequest.GetSnapshotChunk;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.DeleteSnapshotForBootstrapResponse;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotResponse.SnapshotChunkResponse;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.GetScaleUpProgress;
import io.camunda.zeebe.scheduler.AsyncClosable;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotSenderService;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Serves the {@link RequestType#SNAPSHOT} API for a single partition. One handler exists per leader
 * partition and subscribes on its partition's group-scoped topic, so partitions sharing the same
 * number in different partition groups (physical tenants) can never serve each other's snapshot
 * transfer requests.
 */
public class SnapshotApiRequestHandler
    extends AsyncApiRequestHandler<SnapshotApiRequestReader, SnapshotApiResponseWriter> {

  private static final Logger LOG = LoggerFactory.getLogger(SnapshotApiRequestHandler.class);
  private final PartitionId partitionId;
  private final ServerTransport serverTransport;
  private final BrokerClient brokerClient;
  private final Function<ConcurrencyControl, SnapshotSenderService> transferServiceFactory;
  private SnapshotSenderService transferService;

  public SnapshotApiRequestHandler(
      final PartitionId partitionId,
      final ServerTransport serverTransport,
      final BrokerClient brokerClient,
      final Function<ConcurrencyControl, SnapshotSenderService> transferServiceFactory) {
    super(
        "SnapshotApi", partitionId, SnapshotApiRequestReader::new, SnapshotApiResponseWriter::new);
    this.partitionId = partitionId;
    this.serverTransport = serverTransport;
    this.brokerClient = brokerClient;
    this.transferServiceFactory = transferServiceFactory;
  }

  @Override
  public void onActorStarted() {
    transferService = transferServiceFactory.apply(actor);
    serverTransport.subscribe(partitionId, RequestType.SNAPSHOT, this);
    LOG.debug("Serving snapshot transfer requests for partition {}.", partitionId);
  }

  @Override
  public void onActorClosing() {
    AsyncClosable.closeHelper(transferService);
    transferService = null;
    LOG.debug("Stopped serving snapshot transfer requests for partition {}.", partitionId);
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    serverTransport.unsubscribe(partitionId, RequestType.SNAPSHOT);
    return super.closeAsync();
  }

  @Override
  protected ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleAsync(
      final int requestStreamId,
      final long requestId,
      final SnapshotApiRequestReader requestReader,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    return switch (requestReader.getRequest()) {
      case final GetSnapshotChunk snapshotChunkRequest ->
          handleGet(snapshotChunkRequest, responseWriter, errorWriter);
      case final DeleteSnapshotForBootstrapRequest deleteRequest ->
          handleDelete(responseWriter, errorWriter);
    };
  }

  private ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleDelete(
      final SnapshotApiResponseWriter responseWriter, final ErrorResponseWriter errorWriter) {
    final int partitionNumber = partitionId.number();
    return transferService
        .deleteSnapshots(partitionNumber)
        .andThen(
            (response, error) -> {
              final Either<ErrorResponseWriter, SnapshotApiResponseWriter> result;
              if (error != null) {
                LOG.warn("Failed to delete snapshots for partition {}", partitionId, error);
                result = Either.left(errorWriter.internalError(error.getMessage()));
              } else {
                responseWriter.setResponse(new DeleteSnapshotForBootstrapResponse(partitionNumber));
                result = Either.right(responseWriter);
              }
              return CompletableActorFuture.completed(result);
            },
            actor);
  }

  private ActorFuture<Either<ErrorResponseWriter, SnapshotApiResponseWriter>> handleGet(
      final GetSnapshotChunk request,
      final SnapshotApiResponseWriter responseWriter,
      final ErrorResponseWriter errorWriter) {
    final int partitionNumber = partitionId.number();
    if (request.lastChunkName().isPresent() && request.snapshotId().isPresent()) {
      return transferService
          .getNextChunk(
              partitionNumber,
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
                return transferService.getLatestSnapshot(
                    partitionNumber, lastProcessedPosition, request.transferId());
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

  private ActorFuture<Long> getLastProcessedPositionRequired(final UUID transferId) {
    final ActorFuture<Long> lastProcessedPosition = actor.createFuture();
    final var request = new GetScaleUpProgress();
    request.setPartitionGroup(partitionId.group());
    brokerClient
        .sendRequestWithRetry(request)
        .thenApplyAsync(
            r -> {
              final var response = r.getResponseOrThrow();

              LOG.atLevel(Level.DEBUG)
                  .addKeyValue("transferId", transferId)
                  .log("Received response from broker {}", response);
              return response.getScalingPosition();
            },
            actor)
        .whenCompleteAsync(lastProcessedPosition, actor);
    return lastProcessedPosition;
  }
}
