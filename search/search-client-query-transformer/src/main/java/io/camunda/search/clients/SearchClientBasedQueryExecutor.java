/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.aggregation.result.AggregationResultBase;
import io.camunda.search.clients.auth.ResourceAccessChecks;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.function.Function;

public final class SearchClientBasedQueryExecutor {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;

  public SearchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient, final ServiceTransformers transformers) {
    this.searchClient = searchClient;
    this.transformers = transformers;
  }

  public <F extends FilterBase, S extends SortOption, T, R> SearchQueryResult<R> search(
      final TypedSearchQuery<F, S> query,
      final Class<T> documentClass,
      final ResourceAccessChecks resourceAccessChecks) {
    final SearchQueryResultTransformer<T, R> responseTransformer =
        (SearchQueryResultTransformer<T, R>) getSearchResultTransformer(documentClass);
    final var type = query.page().resultType();
    final boolean reverse;
    final SearchQueryResponse<T> response;

    switch (type) {
      case UNLIMITED -> {
        reverse = false;
        response = executeUnlimitedSearch(query, documentClass, resourceAccessChecks);
      }
      case SINGLE_RESULT -> {
        reverse = false;
        response = executeSingleResultSearch(query, documentClass, resourceAccessChecks);
      }
      default -> {
        reverse = !query.page().isNextPage();
        response = executePaginatedSearch(query, documentClass, resourceAccessChecks);
      }
    }

    return responseTransformer.apply(response, reverse);
  }

  public <F extends FilterBase, S extends SortOption, R extends AggregationResultBase> R aggregate(
      final TypedSearchQuery<F, S> query,
      final Class<R> resultClass,
      final ResourceAccessChecks resourceAccessChecks) {
    final var searchQueryResponse =
        executeSearch(
            query,
            searchRequest -> searchClient.search(searchRequest, Object.class),
            resourceAccessChecks);
    return transformers
        .getSearchAggregationResultTransformer(resultClass)
        .apply(searchQueryResponse.aggregations());
  }

  <F extends FilterBase, S extends SortOption, T> SearchQueryResponse<T> executePaginatedSearch(
      final TypedSearchQuery<F, S> query,
      final Class<T> documentClass,
      final ResourceAccessChecks resourceAccessChecks) {
    return executeSearch(query, q -> searchClient.search(q, documentClass), resourceAccessChecks);
  }

  <F extends FilterBase, S extends SortOption, T> SearchQueryResponse<T> executeUnlimitedSearch(
      final TypedSearchQuery<F, S> query,
      final Class<T> documentClass,
      final ResourceAccessChecks resourceAccessChecks) {
    return executeSearch(query, q -> searchClient.scroll(q, documentClass), resourceAccessChecks);
  }

  <F extends FilterBase, S extends SortOption, T> SearchQueryResponse<T> executeSingleResultSearch(
      final TypedSearchQuery<F, S> query,
      final Class<T> documentClass,
      final ResourceAccessChecks resourceAccessChecks) {
    final var response = executePaginatedSearch(query, documentClass, resourceAccessChecks);
    final var hits = response.hits().size();
    if (hits < 1) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_SINGLE_RESULT_NOT_FOUND.formatted(query),
          CamundaSearchException.Reason.NOT_FOUND);
    } else if (hits > 1) {
      throw new CamundaSearchException(
          ErrorMessages.ERROR_SINGLE_RESULT_NOT_UNIQUE.formatted(query),
          CamundaSearchException.Reason.NOT_UNIQUE);
    }
    return response;
  }

  @VisibleForTesting
  <T extends FilterBase, S extends SortOption, R> R executeSearch(
      final TypedSearchQuery<T, S> query,
      final Function<SearchQueryRequest, R> searchExecutor,
      final ResourceAccessChecks resourceAccessChecks) {
    final var transformer = getSearchQueryRequestTransformer(query);
    final var searchRequest = transformer.apply(query, resourceAccessChecks);
    return searchExecutor.apply(searchRequest);
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

  public DocumentBasedSearchClient getSearchClient() {
    return searchClient;
  }
}
