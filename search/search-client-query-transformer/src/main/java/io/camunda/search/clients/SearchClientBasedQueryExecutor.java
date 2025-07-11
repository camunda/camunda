/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.exception.ErrorMessages.ERROR_GET_BY_QUERY_NOT_SUPPORTED_RESULT_TYPE;
import static io.camunda.search.exception.ErrorMessages.ERROR_GET_BY_QUERY_NOT_UNIQUE;

import io.camunda.search.aggregation.result.AggregationResultBase;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.security.ResourceAccessChecks;
import io.camunda.search.clients.source.SearchSourceConfig;
import io.camunda.search.clients.source.SearchSourceFilter;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.clients.transformers.result.ResultConfigTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.page.SearchQueryPage.SearchQueryResultType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.result.QueryResultConfig;
import io.camunda.search.sort.SortOption;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Objects;
import java.util.Optional;
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

    if (Objects.requireNonNull(type) == SearchQueryResultType.UNLIMITED) {
      reverse = false;
      response = executeUnlimitedSearch(query, documentClass, resourceAccessChecks);
    } else {
      reverse = !query.page().isNextPage();
      response = executePaginatedSearch(query, documentClass, resourceAccessChecks);
    }

    return responseTransformer.apply(response, reverse);
  }

  public <T, R> R getById(
      final String id,
      final Class<T> documentClass,
      final String index,
      final ResourceAccessChecks ignored) {
    return getById(id, documentClass, index, ignored, null);
  }

  public <T, R> R getById(
      final String id,
      final Class<T> documentClass,
      final String index,
      final ResourceAccessChecks ignored,
      final QueryResultConfig config) {
    final var builder = new SearchGetRequest.Builder().id(id).index(index);

    Optional.ofNullable(config)
        .map(c -> getResultConfigTransformer(config.getClass()))
        .map(t -> t.apply(config))
        .map(SearchSourceConfig::sourceFilter)
        .map(SearchSourceFilter::excludes)
        .ifPresent(builder::sourceExcludes);

    final var getRequest = builder.build();
    final SearchGetResponse<T> getResponse = searchClient.get(getRequest, documentClass);
    final ServiceTransformer<T, R> transformer =
        (ServiceTransformer<T, R>) getDocumentTransformer(documentClass);
    return Optional.ofNullable(getResponse)
        .filter(SearchGetResponse::found)
        .map(SearchGetResponse::source)
        .map(transformer::apply)
        .orElse(null);
  }

  public <F extends FilterBase, S extends SortOption, T, R> R getByQuery(
      final TypedSearchQuery<F, S> query,
      final Class<T> documentClass,
      final ResourceAccessChecks ignored) {
    final var resultType = query.page().resultType();
    if (!SearchQueryResultType.SINGLE_RESULT.equals(resultType)) {
      throw new CamundaSearchException(
          ERROR_GET_BY_QUERY_NOT_SUPPORTED_RESULT_TYPE.formatted(resultType));
    }

    // execute with disabled authorization checks, with ES/OS the ResourceAccessControl
    // will ensure to return the resource only if the access is granted
    final SearchQueryResult<R> searchResult =
        search(query, documentClass, ResourceAccessChecks.disabled());
    return getSingleDocumentOrThrow(query, searchResult);
  }

  private <R> R getSingleDocumentOrThrow(
      final TypedSearchQuery<?, ?> query, final SearchQueryResult<R> searchResult) {
    if (searchResult.items().size() > 1) {
      throw new CamundaSearchException(ERROR_GET_BY_QUERY_NOT_UNIQUE.formatted(query));
    }
    return searchResult.items().stream().findFirst().orElse(null);
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

  private <T extends QueryResultConfig>
      ResultConfigTransformer<QueryResultConfig> getResultConfigTransformer(final Class<T> clazz) {
    final ServiceTransformer<QueryResultConfig, SearchSourceConfig> transformer =
        transformers.getTransformer(clazz);
    return (ResultConfigTransformer) transformer;
  }
}
