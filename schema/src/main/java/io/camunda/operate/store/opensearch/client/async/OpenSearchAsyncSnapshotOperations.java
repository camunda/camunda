/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
