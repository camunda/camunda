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

import io.camunda.search.clients.control.ResourceAccessControl;
import io.camunda.search.clients.control.ResourceAccessControl.ResourceAccess;
import io.camunda.search.clients.control.ResourceAccessControl.TenantAccess;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FilterBase;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;
import java.util.Optional;

public abstract class IndexFilterTransformer<T extends FilterBase> implements FilterTransformer<T> {

  protected final IndexDescriptor indexDescriptor;
  protected final ResourceAccessControl resourceAccessControl;

  public IndexFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessControl resourceAccessControl) {
    this.indexDescriptor = indexDescriptor;
    this.resourceAccessControl = resourceAccessControl;
  }

  @Override
  public SearchQuery apply(final T filter) {
    final var resourceAccess = resourceAccessControl.resourceAccess();
    final var tenantAccess = resourceAccessControl.tenantAccess();
    return and(
        toSearchQuery(filter),
        applyResourceAccess(resourceAccess),
        applyTenantAccess(tenantAccess));
  }

  @Override
  public IndexDescriptor getIndex() {
    return indexDescriptor;
  }

  @Override
  public abstract FilterTransformer<T> withResourceAccessControl(
      final ResourceAccessControl resourceAccessControl);

  private SearchQuery applyResourceAccess(final ResourceAccess resourceAccess) {
    if (resourceAccess.revoked()) {
      return matchNone();
    }

    if (resourceAccess.granted()) {
      return matchAll();
    }

    if (!resourceAccess.required()) {
      throw new CamundaSearchException(
          "Failed to apply Authorization Check to Search Query with %s".formatted(resourceAccess));
    }

    return Optional.ofNullable(toAuthorizationSearchQuery(resourceAccess.authorization()))
        .orElseThrow(
            () ->
                new CamundaSearchException("Failed to apply authorization check to search query"));
  }

  private SearchQuery applyTenantAccess(final TenantAccess tenantAccess) {
    if (tenantAccess.revoked()) {
      return matchNone();
    }

    if (tenantAccess.granted()) {
      return matchAll();
    }

    if (!tenantAccess.required()) {
      throw new CamundaSearchException(
          "Failed to apply Tenant Check to Search Query with %s".formatted(tenantAccess));
    }

    final var tenantIds = tenantAccess.tenantIds();

    return Optional.ofNullable(toTenantSearchQuery(tenantIds))
        .orElseThrow(
            () -> new CamundaSearchException("Failed to apply tenant check to search query"));
  }

  protected abstract SearchQuery toAuthorizationSearchQuery(Authorization authorization);

  protected abstract SearchQuery toTenantSearchQuery(List<String> tenantIds);
}
