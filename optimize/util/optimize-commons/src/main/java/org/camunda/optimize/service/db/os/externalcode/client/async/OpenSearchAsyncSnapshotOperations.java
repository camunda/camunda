/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.externalcode.client.async;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest.Builder;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotResponse;

@Slf4j
public class OpenSearchAsyncSnapshotOperations extends OpenSearchAsyncOperation {
  public static class SnaphotStatus {
    public static final String FAILED = "FAILED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String SUCCESS = "SUCCESS";
  }

  public OpenSearchAsyncSnapshotOperations(
      OptimizeIndexNameService indexNameService, OpenSearchAsyncClient openSearchAsyncClient) {
    super(indexNameService, openSearchAsyncClient);
  }

  public CompletableFuture<DeleteSnapshotResponse> delete(
      final String repository,
      final String snapshot,
      Function<Exception, String> errorMessageSupplier) {
    final DeleteSnapshotRequest request =
        new Builder().repository(repository).snapshot(snapshot).build();

    return safe(() -> openSearchAsyncClient.snapshot().delete(request), errorMessageSupplier);
  }

  public CompletableFuture<CreateSnapshotResponse> create(
      final String repository,
      final String snapshot,
      final List<String> indexNames,
      Function<Exception, String> errorMessageSupplier) {
    CreateSnapshotRequest request =
        new CreateSnapshotRequest.Builder()
            .repository(repository)
            .snapshot(snapshot)
            .indices(indexNames)
            .includeGlobalState(false)
            .waitForCompletion(true)
            .build();

    return safe(() -> openSearchAsyncClient.snapshot().create(request), errorMessageSupplier);
  }
}
