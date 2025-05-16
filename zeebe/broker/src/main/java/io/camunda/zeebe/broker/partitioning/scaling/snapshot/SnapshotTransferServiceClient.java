/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot;

import io.camunda.zeebe.broker.transport.snapshotapi.GetSnapshotChunkBrokerRequest;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import io.camunda.zeebe.transport.ClientTransport;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class SnapshotTransferServiceClient implements SnapshotTransferService {

  private final ClientTransport client;
  private final Function<Integer, String> nodeAddressProvider;
  private final ConcurrencyControl concurrencyControl;

  public SnapshotTransferServiceClient(
      final ClientTransport client,
      final Function<Integer, String> nodeAddressProvider,
      final ConcurrencyControl concurrencyControl) {
    this.client = client;
    this.nodeAddressProvider = nodeAddressProvider;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public ActorFuture<SnapshotChunk> getLatestSnapshot(final int partition, final UUID transferId) {
    return sendRequest(partition, Optional.empty(), Optional.empty(), transferId);
  }

  @Override
  public ActorFuture<SnapshotChunk> getNextChunk(
      final int partition,
      final String snapshotId,
      final String previousChunkName,
      final UUID transferId) {
    return sendRequest(
        partition, Optional.of(snapshotId), Optional.of(previousChunkName), transferId);
  }

  private ActorFuture<SnapshotChunk> sendRequest(
      final int partition,
      final Optional<String> snapshotId,
      final Optional<String> previousChunkName,
      final UUID transferId) {
    final var request = new GetSnapshotChunk(partition, transferId, snapshotId, previousChunkName);
    final var brokerRequest = new GetSnapshotChunkBrokerRequest(request);
    return client
        .sendRequest(
            () -> nodeAddressProvider.apply(request.partitionId()),
            brokerRequest,
            Duration.ofSeconds(30))
        .thenApply(brokerRequest::getResponse, concurrencyControl)
        .andThen(
            response -> {
              if (response.isResponse()) {
                return CompletableActorFuture.completed(
                    response.getResponse().chunk().orElse(null));
              } else {
                if (response.isRejection()) {
                  return CompletableActorFuture.completedExceptionally(
                      new RuntimeException(response.getRejection().toString()));
                } else {
                  return CompletableActorFuture.completedExceptionally(
                      new RuntimeException("Unexpected response: " + response));
                }
              }
            },
            concurrencyControl);
  }
}
