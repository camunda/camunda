/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import java.io.IOException;
import java.util.*;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.*;
import org.slf4j.Logger;

public class OpenSearchIndexOperations extends OpenSearchRetryOperation {

  public OpenSearchIndexOperations(final Logger logger, final OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  private static String defaultIndexErrorMessage(final String index) {
    return String.format("Failed to search index: %s", index);
  }

  public boolean indexExists(final String index) {
    return safe(
        () -> openSearchClient.indices().exists(r -> r.index(index)).value(),
        e -> defaultIndexErrorMessage(index));
  }

  public void refresh(final String... indexPatterns) {
    final var refreshRequest = new RefreshRequest.Builder().index(List.of(indexPatterns)).build();
    try {
      final RefreshResponse refresh = openSearchClient.indices().refresh(refreshRequest);
      if (refresh.shards().failures().size() > 0) {
        logger.warn("Unable to refresh indices: {}", List.of(indexPatterns));
      }
    } catch (final Exception ex) {
      logger.warn(String.format("Unable to refresh indices: %s", List.of(indexPatterns)), ex);
    }
  }

  private Set<String> getFilteredIndices(final String indexPattern) throws IOException {
    return openSearchClient.indices().get(i -> i.index(List.of(indexPattern))).result().keySet();
  }

  public boolean deleteIndicesWithRetries(final String indexPattern) {
    return executeWithRetries(
        "DeleteIndices " + indexPattern,
        () -> {
          for (final var index : getFilteredIndices(indexPattern)) {
            openSearchClient.indices().delete(d -> d.index(List.of(indexPattern)));
          }
          return true;
        });
  }

  public GetIndexResponse get(final GetIndexRequest.Builder requestBuilder) {
    final GetIndexRequest request = requestBuilder.build();
    return safe(
        () -> openSearchClient.indices().get(request),
        e -> "Failed to get index " + request.index());
  }
}
