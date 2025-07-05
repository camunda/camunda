/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class SearchQueryTenantApplier implements SearchQueryRequestInterceptor {

  private final SecurityContext securityContext;
  private final IndexDescriptor indexDescriptor;

  public SearchQueryTenantApplier(
      final SecurityContext securityContext, final IndexDescriptor indexDescriptor) {
    this.securityContext = securityContext;
    this.indexDescriptor = indexDescriptor;
  }

  @Override
  public SearchQueryRequest apply(final SearchQueryRequest searchQueryRequest) {
    if (securityContext.authentication() == null) {
      return searchQueryRequest;
    }
    final var tenantIds = securityContext.authentication().authenticatedTenantIds();
    return indexDescriptor
        .getTenantIdField()
        .map(
            tenantField -> {
              final SearchQuery tenantQuery = stringTerms(tenantField, tenantIds);
              return searchQueryRequest.toBuilder()
                  .query(SearchQueryBuilders.and(searchQueryRequest.query(), tenantQuery))
                  .build();
            })
        .orElse(searchQueryRequest);
  }
}
