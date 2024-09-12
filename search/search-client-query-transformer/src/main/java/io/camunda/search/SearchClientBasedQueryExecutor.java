/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.search.transformers.filter.AuthenticationTransformer;
import io.camunda.search.transformers.filter.FilterTransformer;
import io.camunda.search.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.transformers.query.TypedSearchQueryTransformer;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.util.Either;

public final class SearchClientBasedQueryExecutor {

  private final DocumentCamundaSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final Authentication authentication;

  public SearchClientBasedQueryExecutor(
      final DocumentCamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.authentication = authentication;
  }

  public <T extends FilterBase, S extends SortOption, R> Either<Exception, SearchQueryResult<R>> search(
      final TypedSearchQuery<T, S> query, final Class<R> documentClass) {
    final var authCheck = getAuthenticationCheckIfPresent();
    final var transformer = getSearchQueryRequestTransformer(query);
    final var searchRequest = transformer.applyWithAuthentication(query, authCheck);

    final SearchQueryResultTransformer<R> responseTransformer = getSearchResultTransformer();
    return searchClient
        .search(searchRequest, documentClass)
        .map(responseTransformer::apply);
  }

  private SearchQuery getAuthenticationCheckIfPresent() {
    if (authentication != null) {
      final var transformer = getAuthenticationTransformer();
      return transformer.apply(authentication);
    }
    return null;
  }

  private <T extends FilterBase, S extends SortOption>
  TypedSearchQueryTransformer<T, S> getSearchQueryRequestTransformer(
          final TypedSearchQuery<T, S> query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass());
  }

  private AuthenticationTransformer getAuthenticationTransformer() {
    final FilterTransformer<Authentication> transformer =
        transformers.getFilterTransformer(Authentication.class);
    return (AuthenticationTransformer) transformer;
  }

  private <R> SearchQueryResultTransformer<R> getSearchResultTransformer() {
    return new SearchQueryResultTransformer<R>();
  }

  private SearchQueryExecutionException rethrowRuntimeException(final Exception e) {
    return new SearchQueryExecutionException("Failed to execute search query", e);
  }
}
