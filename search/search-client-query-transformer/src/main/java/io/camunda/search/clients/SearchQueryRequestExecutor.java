/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.exception.CamundaSearchException.Reason.SEARCH_SERVER_FAILED;
import static io.camunda.search.exception.ErrorMessages.ERROR_NOT_FOUND_ENTITY_BY_KEY;
import static io.camunda.search.exception.ErrorMessages.ERROR_RESULT_TYPE_UNKNOWN;
import static io.camunda.search.exception.ErrorMessages.FORBIDDEN_ACCESS_TO_ENTITY_BY_KEY;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.aggregation.result.AggregationResultBase;
import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.page.SearchQueryPage.PageResultType;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.Optional;
import java.util.function.Function;

public final class SearchQueryRequestExecutor {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;
  private final AuthorizationQueryStrategy authorizationQueryStrategy;

  public SearchQueryRequestExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final AuthorizationQueryStrategy authorizationQueryStrategy,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.authorizationQueryStrategy = authorizationQueryStrategy;
    this.securityContext = securityContext;
  }

  public <R, T> R get(
      final String documentId,
      final Class<T> documentClass,
      final String indexName,
      final Function<R, String> resourceKeySupplier,
      final Function<Reason, String> exceptionMessageSupplier) {
    final ServiceTransformer<T, R> documentTransformer = transformers.getTransformer(documentClass);
    final var getRequest = SearchGetRequest.of(r -> r.id(documentId).index(indexName));
    final SearchGetResponse<T> getResponse = searchClient.get(getRequest, documentClass);

    final var transformedDocument =
        Optional.of(getResponse)
            .filter(SearchGetResponse::found)
            .map(SearchGetResponse::source)
            .map(documentTransformer::apply)
            .orElseThrow(
                () -> handleDocumentNotFound(documentId, documentClass, exceptionMessageSupplier));

    //    final var authorizationResourceId = resourceKeySupplier.apply(transformedDocument);
    //    if (!isAllowedToAccessResource(authorizationResourceId)) {
    //      throw handleDocumentForbiddenAccess(
    //          documentClass, resourceKeySupplier.apply(transformedDocument),
    // exceptionMessageSupplier);
    //    }

    return transformedDocument;
  }

  private <T> CamundaSearchException handleDocumentNotFound(
      final String documentId,
      final Class<T> documentClass,
      final Function<Reason, String> exceptionMessageSupplier) {
    return Optional.ofNullable(exceptionMessageSupplier)
        .map(s -> s.apply(Reason.NOT_FOUND))
        .map(message -> new CamundaSearchException(message, Reason.NOT_FOUND))
        .orElseGet(
            () ->
                new CamundaSearchException(
                    ERROR_NOT_FOUND_ENTITY_BY_KEY.formatted(documentClass.toString(), documentId)));
  }

  private boolean isAllowedToAccessResource(final String resourceId) {
    return authorizationQueryStrategy.canAccessResource(resourceId, securityContext);
  }

  private CamundaSearchException handleDocumentForbiddenAccess(
      final Class<?> documentClass,
      final String authorizationResourceId,
      final Function<Reason, String> exceptionMessageSupplier) {
    final var message =
        Optional.ofNullable(exceptionMessageSupplier)
            .map(s -> s.apply(Reason.FORBIDDEN))
            .orElse(
                FORBIDDEN_ACCESS_TO_ENTITY_BY_KEY.formatted(
                    documentClass.toString(), authorizationResourceId));
    return new CamundaSearchException(message, Reason.FORBIDDEN);
  }

  public <F extends FilterBase, S extends SortOption, T, R> SearchQueryResult<R> search(
      final TypedSearchQuery<F, S> query, final Class<T> documentClass) {
    final SearchQueryResultTransformer<T, R> responseTransformer =
        (SearchQueryResultTransformer<T, R>) getSearchResultTransformer(documentClass);
    final var resultType = query.page().type();
    return executeSearch(
        query,
        q ->
            responseTransformer.apply(
                executeSearchRequest(q, documentClass, resultType), !query.page().isNextPage()));
  }

  public <F extends FilterBase, A extends AggregationBase, R extends AggregationResultBase>
      R aggregate(final TypedSearchAggregationQuery<F, A> query, final Class<R> resultClass) {
    final var searchQueryResponse =
        executeSearch(query, searchRequest -> searchClient.search(searchRequest, Object.class));
    return transformers
        .getSearchAggregationResultTransformer(resultClass)
        .apply(searchQueryResponse.aggregations());
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

  <R> SearchQueryResponse<R> executeSearchRequest(
      final SearchQueryRequest searchRequest,
      final Class<R> documentClass,
      final PageResultType type) {
    final SearchQueryResponse<R> response;
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

  <R> SearchQueryResponse<R> executeSingleResult(
      final SearchQueryRequest searchRequest, final Class<R> documentClass) {
    final var response = searchClient.search(searchRequest, documentClass);
    if (response.hits().size() <= 1) {
      return response;
    }
    throw new CamundaSearchException(
        ErrorMessages.ERROR_NOT_UNIQUE_QUERY, CamundaSearchException.Reason.NOT_UNIQUE);
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
