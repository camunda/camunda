/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients;

import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.OpensearchTransformers;
import io.camunda.data.transformers.search.SearchResponseTransformer;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;

public class OpensearchDataStoreClient implements DataStoreClient {

  private final OpenSearchClient client;
  private final OpensearchTransformers transformers;

  public OpensearchDataStoreClient(final OpenSearchClient client) {
    this.client = client;
    this.transformers = new OpensearchTransformers();
  }

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      final DataStoreSearchResponse<T> response =
          searchResponseTransformer.apply(rawSearchResponse);
      return Either.right(response);
    } catch (final IOException ioe) {
      return Either.left(ioe);
    } catch (final OpenSearchException e) {
      return Either.left(e);
    }
  }

  private DataStoreTransformer<DataStoreSearchRequest, SearchRequest>
      getSearchRequestTransformer() {
    return transformers.getTransformer(DataStoreSearchRequest.class);
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    return new SearchResponseTransformer<>(transformers);
  }
}
