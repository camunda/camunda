/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.exception.ErrorMessages.ERROR_APPLY_AUTHORIZATION_FILTER;
import static io.camunda.search.exception.ErrorMessages.ERROR_APPLY_TENANT_FILTER;
import static io.camunda.search.exception.ErrorMessages.ERROR_UNSUPPORTED_AUTHORIZATION_FILTER;
import static io.camunda.search.exception.ErrorMessages.ERROR_UNSUPPORTED_TENANT_FILTER;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBase;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.AuthorizationBasedResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.security.resource.TenantBasedResourceAccessFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class IndexFilterTransformer<T extends FilterBase> implements FilterTransformer<T> {

  private static final Logger LOG = LoggerFactory.getLogger(IndexFilterTransformer.class);

  protected final IndexDescriptor indexDescriptor;
  protected final ResourceAccessFilter resourceAccessFilter;

  public IndexFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessFilter resourceAccessFilter) {
    this.indexDescriptor = indexDescriptor;
    this.resourceAccessFilter = resourceAccessFilter;
  }

  @Override
  public SearchQuery apply(final T filter) {
    final var filterSearchQuery = toSearchQuery(filter);
    final var authorizationFilterSearchQuery =
        Optional.ofNullable(resourceAccessFilter)
            .map(ResourceAccessFilter::authorizationFilter)
            .map(this::applyAuthorizationFilter)
            .orElse(null);
    final var tenantFilterSearchQuery =
        Optional.ofNullable(resourceAccessFilter)
            .map(ResourceAccessFilter::tenantFilter)
            .map(this::applyTenantFilter)
            .orElse(null);

    return and(filterSearchQuery, authorizationFilterSearchQuery, tenantFilterSearchQuery);
  }

  @Override
  public IndexDescriptor getIndex() {
    return indexDescriptor;
  }

  @Override
  public abstract FilterTransformer<T> withResourceAccessFilter(
      final ResourceAccessFilter resourceAccessFilter);

  private SearchQuery applyAuthorizationFilter(
      final AuthorizationBasedResourceAccessFilter authorizationFilter) {
    if (authorizationFilter.forbidden()) {
      return matchNone();
    }

    if (authorizationFilter.granted()) {
      return matchAll();
    }

    if (!authorizationFilter.requiresCheck()) {
      final var message = ERROR_UNSUPPORTED_AUTHORIZATION_FILTER.formatted(authorizationFilter);
      LOG.error(message);
      throw new CamundaSearchException(message);
    }

    final var authorization = authorizationFilter.requiredAuthorization();
    return Optional.ofNullable(toAuthorizationSearchQuery(authorization))
        .orElseThrow(
            () -> {
              final var message =
                  ERROR_APPLY_AUTHORIZATION_FILTER.formatted(
                      getClass().getSimpleName(), authorizationFilter);
              LOG.error(message);
              return new CamundaSearchException(message);
            });
  }

  private SearchQuery applyTenantFilter(final TenantBasedResourceAccessFilter tenantFilter) {
    if (tenantFilter.forbidden()) {
      return matchNone();
    }

    if (tenantFilter.granted()) {
      return matchAll();
    }

    if (!tenantFilter.requiresCheck()) {
      final var message = ERROR_UNSUPPORTED_TENANT_FILTER.formatted(tenantFilter);
      LOG.error(message);
      throw new CamundaSearchException(message);
    }

    final var tenantIds = tenantFilter.authenticatedTenants();
    return Optional.ofNullable(toTenantSearchQuery(tenantIds))
        .orElseThrow(
            () -> {
              final var message =
                  ERROR_APPLY_TENANT_FILTER.formatted(getClass().getSimpleName(), tenantFilter);
              LOG.error(message);
              return new CamundaSearchException(message);
            });
  }

  protected abstract SearchQuery toAuthorizationSearchQuery(Authorization authorization);

  protected abstract SearchQuery toTenantSearchQuery(List<String> tenantIds);
}
