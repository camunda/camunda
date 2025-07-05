/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import static io.camunda.search.exception.CamundaSearchException.Reason.SEARCH_SERVER_FAILED;
import static io.camunda.search.exception.ErrorMessages.ERROR_RESULT_TYPE_UNKNOWN;

import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.page.SearchQueryPage.PageResultType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.CollectionUtils;

public class SearchQueryExecutor<FIL extends FilterBase, SOR extends SortOption, DOC, RES> {

  private final TypedSearchQueryTransformer<FIL, SOR> searchQueryTransformer;
  private final SearchQueryResultTransformer<DOC, RES> searchQueryResultTransformer;
  private final DocumentBasedSearchClient searchClient;
  private final List<SearchQueryRequestInterceptor> interceptors;

  public SearchQueryExecutor(
      final TypedSearchQueryTransformer<FIL, SOR> searchQueryTransformer,
      final SearchQueryResultTransformer<DOC, RES> searchQueryResultTransformer,
      final DocumentBasedSearchClient searchClient,
      final List<SearchQueryRequestInterceptor> interceptors) {
    this.searchQueryTransformer = searchQueryTransformer;
    this.searchQueryResultTransformer = searchQueryResultTransformer;
    this.searchClient = searchClient;
    this.interceptors = interceptors;
  }

  public SearchQueryResult<RES> execute(
      final TypedSearchQuery<FIL, SOR> query, final Class<DOC> documentClass) {
    // transform query into a search request
    final var transformedSearchQueryRequest = searchQueryTransformer.apply(query);
    // apply interceptors to adjust query if required
    final var finalSearchQueryRequest = applyInterceptors(transformedSearchQueryRequest);
    // execute search query request
    final var result =
        executeSearchRequest(finalSearchQueryRequest, documentClass, query.page().type());
    // transform into search query result
    return searchQueryResultTransformer.apply(result, !query.page().isNextPage());
  }

  private SearchQueryResponse<DOC> executeSearchRequest(
      final SearchQueryRequest searchRequest,
      final Class<DOC> documentClass,
      final PageResultType type) {
    final SearchQueryResponse<DOC> response;
    if (type == PageResultType.LIMITED_RESULT) {
      if (searchRequest.size() <= DocumentBasedSearchClient.QUERY_MAX_SIZE) {
        response = searchClient.search(searchRequest, documentClass);
      } else {
        response = searchClient.scroll(searchRequest, documentClass, false);
      }
    } else if (type == PageResultType.UNLIMITED_RESULT) {
      response = searchClient.scroll(searchRequest, documentClass, true);
    } else if (type == PageResultType.SINGLE_RESULT) {
      response = executeSingleResult(searchRequest, documentClass);
    } else {
      throw new CamundaSearchException(
          ERROR_RESULT_TYPE_UNKNOWN.formatted(String.valueOf(type)), SEARCH_SERVER_FAILED);
    }
    return response;
  }

  private SearchQueryResponse<DOC> executeSingleResult(
      final SearchQueryRequest searchRequest, final Class<DOC> documentClass) {
    final var response = searchClient.search(searchRequest, documentClass);
    if (response.hits().size() <= 1) {
      return response;
    }
    throw new CamundaSearchException(
        ErrorMessages.ERROR_NOT_UNIQUE_QUERY, CamundaSearchException.Reason.NOT_UNIQUE);
  }

  private SearchQueryRequest applyInterceptors(final SearchQueryRequest searchQueryRequest) {
    SearchQueryRequest request = searchQueryRequest;
    for (final var interceptor : interceptors) {
      request = interceptor.apply(request);
    }
    return request;
  }

  public static class Builder<FIL extends FilterBase, SOR extends SortOption, DOC, RES> {

    private final DocumentBasedSearchClient searchClient;
    private TypedSearchQueryTransformer<FIL, SOR> searchQueryTransformer;
    private SearchQueryResultTransformer<DOC, RES> searchQueryResultTransformer;
    private final List<SearchQueryRequestInterceptor> interceptors = new ArrayList<>();

    public Builder(final DocumentBasedSearchClient searchClient) {
      this.searchClient = searchClient;
    }

    public Builder<FIL, SOR, DOC, RES> searchQueryTransformer(
        final TypedSearchQueryTransformer<FIL, SOR> value) {
      searchQueryTransformer = value;
      return this;
    }

    public Builder<FIL, SOR, DOC, RES> searchQueryResultTransformer(
        final SearchQueryResultTransformer<DOC, RES> value) {
      searchQueryResultTransformer = value;
      return this;
    }

    public Builder<FIL, SOR, DOC, RES> interceptors(
        final List<SearchQueryRequestInterceptor> values) {
      if (!CollectionUtils.isEmpty(values)) {
        interceptors.addAll(values);
      }
      return this;
    }

    public Builder<FIL, SOR, DOC, RES> interceptor(final SearchQueryRequestInterceptor value) {
      if (value != null) {
        interceptors.add(value);
      }
      return this;
    }

    public SearchQueryExecutor<FIL, SOR, DOC, RES> build() {
      return new SearchQueryExecutor<>(
          searchQueryTransformer, searchQueryResultTransformer, searchClient, interceptors);
    }
  }
}
