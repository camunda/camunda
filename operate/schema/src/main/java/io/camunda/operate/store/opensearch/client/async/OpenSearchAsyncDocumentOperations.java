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
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;

public class OpenSearchAsyncDocumentOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncDocumentOperations(
      Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  // TODO check unsued
  private CompletableFuture<ScrollResponse<Object>> scrollAsync(
      final ScrollRequest scrollRequest, Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchAsyncClient.scroll(scrollRequest, Object.class), errorMessageSupplier);
  }

  public CompletableFuture<DeleteByQueryResponse> delete(
      DeleteByQueryRequest.Builder requestBuilder,
      Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchAsyncClient.deleteByQuery(requestBuilder.build()), errorMessageSupplier);
  }

  public <R> CompletableFuture<SearchResponse<R>> search(
      SearchRequest.Builder searchRequestBuilder,
      Class<R> clazz,
      Function<Exception, String> errorMessageSupplier) {
    return safe(
        () -> openSearchAsyncClient.search(searchRequestBuilder.build(), clazz),
        errorMessageSupplier);
  }
}
