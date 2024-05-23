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
import io.camunda.data.clients.core.DataStoreClientBase;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.ElasticsearchRequestBuilders;
import io.camunda.data.clients.core.ElasticsearchSearchRequest;
import io.camunda.data.clients.core.ElasticsearchSearchResponse;
import io.camunda.data.clients.query.ElasticsearchQueryBuilders;
import io.camunda.data.clients.types.ElasticsearchSortOptionsBuilders;
import io.camunda.util.DataStoreObjectBuilder;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.function.Function;

public class ElasticsearchDataStoreClient extends DataStoreClientBase implements DataStoreClient {

  private static final ElasticsearchQueryBuilders BUILDERS_DELEGATE =
      new ElasticsearchQueryBuilders();
  private static final ElasticsearchRequestBuilders REQUEST_DELEGATE =
      new ElasticsearchRequestBuilders();
  private static final ElasticsearchSortOptionsBuilders SORT_OPTIONS_DELEGATE =
      new ElasticsearchSortOptionsBuilders();

  private final ElasticsearchClient client;

  public ElasticsearchDataStoreClient(final ElasticsearchClient client) {
    super(BUILDERS_DELEGATE, REQUEST_DELEGATE, SORT_OPTIONS_DELEGATE);
    this.client = client;
  }

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
    final var request = (SearchRequest) searchRequest.get();

    try {
      final var rawSearchResponse = client.search(request, documentClass);
      final var searchResponse = ElasticsearchSearchResponse.from(rawSearchResponse);
      return Either.right(searchResponse);
    } catch (final IOException ioe) {
      return Either.left(ioe);
    } catch (final ElasticsearchException e) {
      return Either.left(e);
    }
  }

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final Function<DataStoreSearchRequest.Builder, DataStoreObjectBuilder<DataStoreSearchRequest>>
          fn,
      final Class<T> documentClass) {
    return search(fn.apply(new ElasticsearchSearchRequest.Builder()).build(), documentClass);
  }
}
