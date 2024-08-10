/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.async;

import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotResponse;
import org.slf4j.Logger;

public class OpenSearchAsyncSnapshotOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncSnapshotOperations(
      Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  public CompletableFuture<DeleteSnapshotResponse> delete(
      DeleteSnapshotRequest.Builder requestBuilder) {
    return safe(
        () -> openSearchAsyncClient.snapshot().delete(requestBuilder.build()),
        e -> "Failed to send snapshot delete request!");
  }

  public CompletableFuture<CreateSnapshotResponse> create(
      CreateSnapshotRequest.Builder requestBuilder) {
    return safe(
        () -> openSearchAsyncClient.snapshot().create(requestBuilder.build()),
        e -> "Failed to send snapshot create request!");
  }
}
