/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.async;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;

public class OpenSearchAsyncDocumentOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncDocumentOperations(
      final Logger logger, final OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  public <R> CompletableFuture<SearchResponse<R>> search(
      final SearchRequest.Builder searchRequestBuilder,
      final Class<R> clazz,
      final Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchAsyncClient.search(searchRequestBuilder.build(), clazz),
        errorMessageSupplier);
  }
}
