/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.exception.SearchQueryExecutionException;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.transformers.search.SearchResponseTransformer;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public class OpensearchSearchClient implements DocumentBasedSearchClient, AutoCloseable {

  private final OpenSearchClient client;
  private final OpensearchTransformers transformers;

  public OpensearchSearchClient(final OpenSearchClient client) {
    this(client, new OpensearchTransformers());
  }

  public OpensearchSearchClient(
      final OpenSearchClient client, final OpensearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> SearchQueryResponse<T> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      return searchResponseTransformer.apply(rawSearchResponse);
    } catch (final IOException | OpenSearchException e) {
      throw new SearchQueryExecutionException("Failed to execute search query", e);
    }
  }

  private SearchTransfomer<SearchQueryRequest, SearchRequest> getSearchRequestTransformer() {
    return transformers.getTransformer(SearchQueryRequest.class);
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    return new SearchResponseTransformer<>(transformers);
  }

  @Override
  public void close() throws Exception {
    if (client != null) {
      try {
        client._transport().close();
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
