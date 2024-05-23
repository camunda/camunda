/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients;

import io.camunda.data.clients.core.DataStoreClientBase;
import io.camunda.data.clients.core.DataStoreSearchRequest;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.core.OpensearchRequestBuilders;
import io.camunda.data.clients.core.OpensearchSearchRequest;
import io.camunda.data.clients.core.OpensearchSearchResponse;
import io.camunda.data.clients.query.OpensearchQueryBuilders;
import io.camunda.data.clients.types.OpensearchSortOptionsBuilders;
import io.camunda.util.DataStoreObjectBuilder;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.util.function.Function;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.SearchRequest;

public class OpensearchDataStoreClient extends DataStoreClientBase implements DataStoreClient {

  private static final OpensearchQueryBuilders BUILDERS_DELEGATE = new OpensearchQueryBuilders();
  private static final OpensearchRequestBuilders REQUEST_DELEGATE = new OpensearchRequestBuilders();
  private static final OpensearchSortOptionsBuilders SORT_OPTIONS_DELEGATE =
      new OpensearchSortOptionsBuilders();

  private final OpenSearchClient client;

  public OpensearchDataStoreClient(final OpenSearchClient client) {
    super(BUILDERS_DELEGATE, REQUEST_DELEGATE, SORT_OPTIONS_DELEGATE);
    this.client = client;
  }

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final DataStoreSearchRequest searchRequest, final Class<T> documentClass) {
    final var request = (SearchRequest) searchRequest.get();

    try {
      final var rawSearchResponse = client.search(request, documentClass);
      final var searchResponse = OpensearchSearchResponse.from(rawSearchResponse);
      return Either.right(searchResponse);
    } catch (final IOException ioe) {
      return Either.left(ioe);
    } catch (final OpenSearchException e) {
      return Either.left(e);
    }
  }

  @Override
  public <T> Either<Exception, DataStoreSearchResponse<T>> search(
      final Function<DataStoreSearchRequest.Builder, DataStoreObjectBuilder<DataStoreSearchRequest>>
          fn,
      final Class<T> documentClass) {
    return search(fn.apply(new OpensearchSearchRequest.Builder()).build(), documentClass);
  }
}
