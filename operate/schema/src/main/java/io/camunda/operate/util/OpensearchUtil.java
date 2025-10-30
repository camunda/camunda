/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.ReindexRequest;
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpensearchUtil {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchUtil.class);

  private OpensearchUtil() {
    // Utility class, no instantiation
  }

  public static <T> Either<Throwable, Long> handleResponse(
      final T response, final Throwable error, final String sourceIndexName) {
    final String operation = response instanceof ReindexResponse ? "reindex" : "deleteByQuery";
    if (error != null) {
      final var message =
          String.format(
              "Exception occurred, while performing operation %s on source index %s. Error: %s",
              operation, sourceIndexName, error.getMessage());
      return Either.left(new ArchiverException(message, error));
    }

    final var bulkFailures =
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).failures()
            : ((DeleteByQueryResponse) response).failures();

    if (!bulkFailures.isEmpty()) {
      LOGGER.error(
          "Failures occurred when performing operation: {} on source index {}. See details below.",
          operation,
          sourceIndexName);
      bulkFailures.forEach(f -> LOGGER.error(f.toString()));
      return Either.left(new ArchiverException(String.format("Operation %s failed", operation)));
    }

    LOGGER.debug("Operation {} succeeded on source index {}", operation, sourceIndexName);
    return Either.right(
        response instanceof ReindexResponse
            ? ((ReindexResponse) response).total()
            : ((DeleteByQueryResponse) response).total());
  }

  public static <T> CompletableFuture<SearchResponse<T>> searchAsync(
      final SearchRequest request, final Class<T> tClass, final OpenSearchAsyncClient client) {
    try {
      return client.search(request, tClass);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<ReindexResponse> reindexAsync(
      final ReindexRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.reindex(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  public static CompletableFuture<DeleteByQueryResponse> deleteAsync(
      final DeleteByQueryRequest request, final OpenSearchAsyncClient client) {
    try {
      return client.deleteByQuery(request);
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }
  }
}
