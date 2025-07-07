/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.resource.ResourceAccessPolicy;
import java.util.Optional;

public class SearchClientBasedGetExecutor {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final SecurityContext securityContext;
  private final ResourceAccessPolicy resourceAccessPolicy;

  public SearchClientBasedGetExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final ResourceAccessPolicy resourceAccessPolicy) {
    this(searchClient, transformers, resourceAccessPolicy, null);
  }

  public SearchClientBasedGetExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final ResourceAccessPolicy resourceAccessPolicy,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.resourceAccessPolicy = resourceAccessPolicy;
    this.securityContext = securityContext;
  }

  public SearchClientBasedGetExecutor withSecurityContext(final SecurityContext securityContext) {
    return new SearchClientBasedGetExecutor(
        searchClient, transformers, resourceAccessPolicy, securityContext);
  }

  public <R, T> R getById(final String id, final Class<T> documentClass, final String indexName) {
    final var request = SearchGetRequest.of(b -> b.id(id).index(indexName));
    final SearchGetResponse<T> getResponse = searchClient.get(request, documentClass);
    final ServiceTransformer<T, R> documentTransformer = transformers.getTransformer(documentClass);
    final var document =
        Optional.ofNullable(getResponse)
            .filter(SearchGetResponse::found)
            .map(SearchGetResponse::source)
            .map(documentTransformer::apply)
            .orElse(null);
    final var resourceAccessFilter =
        resourceAccessPolicy.withResource(document).applySecurityContext(securityContext);
    return document;
  }

  public <F extends FilterBase, S extends SortOption, T, R> R getByQuery(
      final TypedSearchQuery<F, S> query, final Class<T> documentClass) {
    final var transformer = getSearchQueryRequestTransformer(query);
    final SearchQueryResultTransformer<T, R> responseTransformer =
        (SearchQueryResultTransformer<T, R>) getSearchResultTransformer(documentClass);
    final var searchRequest = transformer.apply(query);
    final var searchResponse = searchClient.search(searchRequest, documentClass);
    final var searchResult = responseTransformer.apply(searchResponse, false);
    final var document = searchResult.items().getFirst();
    final ResourceAccessPolicy<R> resourceAccessFilter =
        resourceAccessPolicy.withResource(document);
    resourceAccessFilter.applySecurityContext(securityContext);
    return document;
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
