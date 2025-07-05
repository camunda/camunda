/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import io.camunda.search.aggregation.AggregationBase;
import io.camunda.search.aggregation.result.AggregationResultBase;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.auth.AuthorizationQueryTransformers;
import io.camunda.search.clients.transformers.query.SearchQueryResultTransformer;
import io.camunda.search.clients.transformers.query.TypedSearchQueryTransformer;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.TypedSearchAggregationQuery;
import io.camunda.search.query.TypedSearchQuery;
import io.camunda.search.sort.SortOption;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;

public class SearchClientExecutor {

  private final DocumentBasedSearchClient searchClient;
  private final ServiceTransformers transformers;
  private final IndexDescriptors indexDescriptors;
  private final AuthorizationChecker authorizationChecker;
  private final SecurityContext securityContext;

  public SearchClientExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final AuthorizationChecker authorizationChecker) {
    this(searchClient, transformers, indexDescriptors, authorizationChecker, null);
  }

  public SearchClientExecutor(
      final DocumentBasedSearchClient searchClient,
      final ServiceTransformers transformers,
      final IndexDescriptors indexDescriptors,
      final AuthorizationChecker authorizationChecker,
      final SecurityContext securityContext) {
    this.searchClient = searchClient;
    this.transformers = transformers;
    this.indexDescriptors = indexDescriptors;
    this.authorizationChecker = authorizationChecker;
    this.securityContext = securityContext;
  }

  public SearchClientExecutor withSecurityContext(final SecurityContext securityContext) {
    return new SearchClientExecutor(
        searchClient, transformers, indexDescriptors, authorizationChecker, securityContext);
  }

  public <FIL extends FilterBase, SOR extends SortOption, DOC, RES> SearchQueryResult<RES> search(
      final TypedSearchQuery<FIL, SOR> query, final Class<DOC> documentClass) {
    return (SearchQueryResult<RES>)
        buildSearchQueryRequestExecutor(query, documentClass).execute(query, documentClass);
  }

  public <FIL extends FilterBase, AGG extends AggregationBase, RES extends AggregationResultBase>
      RES aggregate(
          final TypedSearchAggregationQuery<FIL, AGG> query, final Class<RES> documentClass) {

    return null;
  }

  public <RES, DOC> RES get(
      final String id, final Class<DOC> documentClass, final String indexName) {
    final var searchRequest = SearchGetRequest.of(b -> b.id(id).index(indexName));

    return (RES) buildSearchGetRequestExecutor(documentClass).execute(searchRequest, documentClass);
  }

  private <FIL extends FilterBase, SOR extends SortOption, DOC, RES>
      SearchQueryExecutor<FIL, SOR, DOC, RES> buildSearchQueryRequestExecutor(
          final TypedSearchQuery<FIL, SOR> query, final Class<DOC> documentClass) {
    final var authorizationApplier =
        new SearchQueryAuthorizationApplier(
            securityContext,
            authorizationChecker,
            AuthorizationQueryTransformers.getTransformer(query.getClass()));

    final var tenantApplier =
        new SearchQueryTenantApplier(
            securityContext,
            transformers.getFilterTransformer(query.filter().getClass()).getIndex());

    final TypedSearchQueryTransformer<FIL, SOR> searchQueryRequestTransformer =
        getSearchQueryRequestTransformer(query);
    final SearchQueryResultTransformer<DOC, RES> searchQueryResultTransformer =
        getSearchResultTransformer(documentClass);

    return new SearchQueryExecutor.Builder<FIL, SOR, DOC, RES>(searchClient)
        .searchQueryTransformer(searchQueryRequestTransformer)
        .interceptor(authorizationApplier)
        .interceptor(tenantApplier)
        .searchQueryResultTransformer(searchQueryResultTransformer)
        .build();
  }

  private <FIL extends FilterBase, SOR extends SortOption>
      TypedSearchQueryTransformer<FIL, SOR> getSearchQueryRequestTransformer(
          final TypedSearchQuery<FIL, SOR> query) {
    return transformers.getTypedSearchQueryTransformer(query.getClass());
  }

  private <DOC, RES> SearchQueryResultTransformer<DOC, RES> getSearchResultTransformer(
      final Class<DOC> documentClass) {
    return new SearchQueryResultTransformer<>(getDocumentTransformer(documentClass));
  }

  private <DOC, RES> ServiceTransformer<DOC, RES> getDocumentTransformer(
      final Class<DOC> documentClass) {
    return transformers.getTransformer(documentClass);
  }

  private <DOC, RES> SearchGetExecutor<DOC, RES> buildSearchGetRequestExecutor(
      final Class<DOC> documentClass) {
    final ServiceTransformer<DOC, RES> documentTransformer =
        transformers.getTransformer(documentClass);

    return new SearchGetExecutor<DOC, RES>(
        documentTransformer,
        searchClient,
        new SearchGetAuthorizationApplier<RES>(securityContext, authorizationChecker));
  }
}
