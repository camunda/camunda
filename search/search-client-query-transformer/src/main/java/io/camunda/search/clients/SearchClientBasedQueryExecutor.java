/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;
import java.util.function.Function;

public final class SearchClientBasedQueryExecutor {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;
  private final AuthorizationQueryStrategy authorizationQueryStrategy;

  public SearchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final AuthorizationQueryStrategy authorizationQueryStrategy,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.authorizationQueryStrategy = authorizationQueryStrategy;
    this.securityContext = securityContext;
  }

  public <F extends FilterBase, S extends SortOption, T, R> SearchQueryResult<R> search(
      final TypedSearchQuery<F, S> query, final Class<T> documentClass) {
    final SearchQueryResultTransformer<T, R> responseTransformer =
        (SearchQueryResultTransformer<T, R>) getSearchResultTransformer(documentClass);
    return executeSearch(
        query,
        q ->
            responseTransformer.apply(
                searchClient.search(q, documentClass), !query.page().isNextPage()));
  }

  public <F extends FilterBase, S extends SortOption, T, R> List<R> findAll(
      final TypedSearchQuery<F, S> query, final Class<T> documentClass) {
    final ServiceTransformer<T, R> documentTransformer =
        (ServiceTransformer<T, R>) getDocumentTransformer(documentClass);
    return executeSearch(
        query,
        q ->
            searchClient.findAll(q, documentClass).stream()
                .map(documentTransformer::apply)
                .toList());
  }

  public <T extends FilterBase, R> R aggregate(
      final TypedSearchAggregationQuery<T> query, final Class<R> documentClass) {
    final var searchAggregationResult = executeSearch(query, searchClient::aggregate);
    return transformers
        .getSearchAggregationResultTransformer(documentClass)
        .apply(searchAggregationResult);
  }

  @VisibleForTesting
  <T extends FilterBase, S extends SortOption, R> R executeSearch(
      final TypedSearchQuery<T, S> query, final Function<SearchQueryRequest, R> searchExecutor) {
    final var transformer = getSearchQueryRequestTransformer(query);
    final var searchRequest = transformer.apply(query);
    final var authenticatedSearchRequest = applyTenantFilter(searchRequest, query);
    final var authorizedSearchRequest =
        authorizationQueryStrategy.applyAuthorizationToQuery(
            authenticatedSearchRequest, securityContext, query.getClass());
    return searchExecutor.apply(authorizedSearchRequest);
  }

  private SearchQueryRequest applyTenantFilter(
      final SearchQueryRequest request, final TypedSearchQuery<?, ?> query) {
    if (securityContext.authentication() == null) {
      return request;
    }
    final var tenantIds = securityContext.authentication().authenticatedTenantIds();
    final IndexDescriptor indexDescriptor =
        transformers.getFilterTransformer(query.filter().getClass()).getIndex();
    return indexDescriptor
        .getTenantIdField()
        .map(
            tenantField -> {
              final SearchQuery tenantQuery = stringTerms(tenantField, tenantIds);
              return request.toBuilder()
                  .query(SearchQueryBuilders.and(request.query(), tenantQuery))
                  .build();
            })
        .orElse(request);
  }

  private <T extends FilterBase, S extends SortOption>
      TypedSearchQueryTransformer<T, S> getSearchQueryRequestTransformer(
          final TypedSearchQuery<T, S> query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass());
  }

  private <T, R> SearchQueryResultTransformer<T, R> getSearchResultTransformer(
      final Class<R> documentClass) {
    return new SearchQueryResultTransformer<>(getDocumentTransformer(documentClass));
  }

  private <T, R> ServiceTransformer<T, R> getDocumentTransformer(final Class<R> documentClass) {
    return transformers.getTransformer(documentClass);
  }
}
