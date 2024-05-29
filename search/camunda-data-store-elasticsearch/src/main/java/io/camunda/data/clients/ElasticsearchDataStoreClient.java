/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.mappers.DataStoreTransformer;
import io.camunda.data.transformers.ElasticsearchTransformers;
import io.camunda.data.transformers.search.SearchResponseTransformer;
import io.camunda.zeebe.util.Either;
import java.io.IOException;

public class ElasticsearchDataStoreClient implements DataStoreClient {

  private final ElasticsearchClient client;
  private final ElasticsearchTransformers transformers;

  public ElasticsearchDataStoreClient(final ElasticsearchClient client) {
    this(client, new ElasticsearchTransformers());
  }

  public ElasticsearchDataStoreClient(
      final ElasticsearchClient client, final ElasticsearchTransformers transformer) {
    this.client = client;
    this.transformers = new ElasticsearchTransformers();
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
    } catch (final ElasticsearchException e) {
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
