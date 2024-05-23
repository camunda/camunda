/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.query;

import io.camunda.data.clients.DataStoreClient;
import io.camunda.data.clients.core.DataStoreSearchResponse;
import io.camunda.data.clients.query.DataStoreQuery;
import io.camunda.service.auth.Authentication;
import io.camunda.service.query.filter.FilterBody;
import io.camunda.zeebe.util.Either;

public class SearchQueryExecutor {

  private final DataStoreClient dataStoreClient;
  private final Authentication authentication;

  public SearchQueryExecutor(
      final DataStoreClient dataStoreClient, final Authentication authentication) {
    this.dataStoreClient = dataStoreClient;
    this.authentication = authentication;
  }

  public <T extends FilterBody, R> SearchQueryResult<R> search(
      final SearchQuery<T> query, final Class<R> documentClass) {
    final var authQuery = getAuthenticationQueryIfPresent();
    final var response = searchWithAuthenticationQuery(query, documentClass, authQuery);
    return response.fold(
        SearchQueryResult::from,
        (e) -> {
          throw rethrowRuntimeException(e);
        });
  }

  private DataStoreQuery getAuthenticationQueryIfPresent() {
    if (authentication != null) {
      return authentication.toSearchQuery();
    }
    return null;
  }

  private <T extends FilterBody, R>
      Either<Exception, DataStoreSearchResponse<R>> searchWithAuthenticationQuery(
          final SearchQuery<T> query,
          final Class<R> documentClass,
          final DataStoreQuery authQuery) {
    final var searchRequest = query.toSearchRequest(authQuery);
    return dataStoreClient.search(searchRequest, documentClass);
  }

  private RuntimeException rethrowRuntimeException(final Exception e) {
    return new RuntimeException("something went wrong", e);
  }
}
