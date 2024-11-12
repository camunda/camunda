/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client.async;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.UpdateByQueryRequest;
import org.opensearch.client.opensearch.core.UpdateByQueryResponse;

public class OpenSearchAsyncDocumentOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncDocumentOperations(
      final OptimizeIndexNameService indexNameService,
      final OpenSearchAsyncClient openSearchAsyncClient) {
    super(indexNameService, openSearchAsyncClient);
  }

  public CompletableFuture<UpdateByQueryResponse> updateByQuery(
      final UpdateByQueryRequest.Builder requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    requestBuilder.waitForCompletion(false);
    return safe(
        () -> openSearchAsyncClient.updateByQuery(requestBuilder.build()), errorMessageSupplier);
  }

  public CompletableFuture<DeleteByQueryResponse> deleteByQuery(
      final DeleteByQueryRequest.Builder requestBuilder,
      final Function<Exception, String> errorMessageSupplier) {
    requestBuilder.waitForCompletion(false);
    return safe(
        () -> openSearchAsyncClient.deleteByQuery(requestBuilder.build()), errorMessageSupplier);
  }
}
