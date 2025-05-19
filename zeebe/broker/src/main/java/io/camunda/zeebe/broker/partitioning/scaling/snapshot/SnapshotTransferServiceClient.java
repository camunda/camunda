/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.scaling.snapshot;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.transport.snapshotapi.GetSnapshotChunkBrokerRequest;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.snapshots.transfer.SnapshotTransferService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SnapshotTransferServiceClient implements SnapshotTransferService {

  private final BrokerClient client;

  public SnapshotTransferServiceClient(final BrokerClient client) {
    this.client = client;
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
    final var future = new CompletableActorFuture<SnapshotChunk>();
    client
        .sendRequestWithRetry(brokerRequest, Duration.ofSeconds(30))
        .thenCompose(
            response -> {
              if (response.isResponse()) {
                return CompletableFuture.completedFuture(
                    response.getResponse().chunk().orElse(null));
              } else {
                if (response.isRejection()) {
                  return CompletableFuture.failedFuture(
                      new RuntimeException(response.getRejection().toString()));
                } else {
                  return CompletableFuture.failedFuture(
                      new RuntimeException("Unexpected response: " + response));
                }
              }
            })
        .whenComplete(future);

    return future;
  }
}
