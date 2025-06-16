/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.snapshotapi;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.partitioning.scaling.snapshot.SnapshotChunkResponse;
import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler;
import io.camunda.zeebe.broker.transport.ErrorResponseWriter;
import io.camunda.zeebe.gateway.impl.broker.request.scaling.GetScaleUpProgress;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import io.camunda.zeebe.transport.RequestType;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
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
  private final ConcurrentMap<Integer, SnapshotTransferService> transferServices =
      new ConcurrentHashMap<>();
  private final AtomixServerTransport serverTransport;
  private final BrokerClient brokerClient;

  protected SnapshotApiRequestHandler(
      final AtomixServerTransport serverTransport, final BrokerClient brokerClient) {
    super(SnapshotApiRequestReader::new, SnapshotApiResponseWriter::new);
    this.serverTransport = serverTransport;
    this.brokerClient = brokerClient;
  }

  public void addTransferService(
      final int partitionId, final SnapshotTransferService transferService) {
    serverTransport.subscribe(partitionId, RequestType.SNAPSHOT, this);
    transferServices.put(partitionId, transferService);
    LOG.debug("Added SnapshotTransferService for partition {}.", partitionId);
  }

  public void removeTransferService(final int partitionId) {
    serverTransport.unsubscribe(partitionId, RequestType.SNAPSHOT);
    transferServices.remove(partitionId);
    LOG.debug("Removed SnapshotTransferService for partition {}.", partitionId);
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
                  return service
                      .getLatestSnapshot(partitionId, lastProcessedPosition, request.transferId())
                      .thenApply(
                          chunk -> {
                            responseWriter.setResponse(
                                new SnapshotChunkResponse(
                                    request.transferId(), Optional.ofNullable(chunk)));
                            return Either.right(responseWriter);
                          });
                },
                actor);
      }
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
              return r.getResponse().getBootstrappedAt();
            })
        .whenComplete(lastProcessedPosition);
    return lastProcessedPosition;
  }
}
