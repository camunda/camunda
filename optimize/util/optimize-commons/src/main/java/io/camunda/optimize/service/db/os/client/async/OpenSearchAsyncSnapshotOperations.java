/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.async;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotResponse;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest.Builder;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotResponse;

public class OpenSearchAsyncSnapshotOperations extends OpenSearchAsyncOperation {

  public OpenSearchAsyncSnapshotOperations(
      final OptimizeIndexNameService indexNameService,
      final OpenSearchAsyncClient openSearchAsyncClient) {
    super(indexNameService, openSearchAsyncClient);
  }

  public CompletableFuture<DeleteSnapshotResponse> delete(
      final String repository,
      final String snapshot,
      final Function<Exception, String> errorMessageSupplier) {
    final DeleteSnapshotRequest request =
        new Builder().repository(repository).snapshot(snapshot).build();

    return safe(() -> openSearchAsyncClient.snapshot().delete(request), errorMessageSupplier);
  }

  public CompletableFuture<CreateSnapshotResponse> create(
      final String repository,
      final String snapshot,
      final List<String> indexNames,
      final Function<Exception, String> errorMessageSupplier) {
    final CreateSnapshotRequest request =
        new CreateSnapshotRequest.Builder()
            .repository(repository)
            .snapshot(snapshot)
            .indices(indexNames)
            .includeGlobalState(false)
            .waitForCompletion(true)
            .build();

    return safe(() -> openSearchAsyncClient.snapshot().create(request), errorMessageSupplier);
  }

  public static class SnapshotStatus {

    public static final String FAILED = "FAILED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PARTIAL = "PARTIAL";
    public static final String SUCCESS = "SUCCESS";
  }
}
