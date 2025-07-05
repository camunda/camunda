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
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.PERMISSIONS_TYPES;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public final class AuthorizationFilterTransformer
    extends IndexFilterTransformer<AuthorizationFilter> {

  public AuthorizationFilterTransformer(final IndexDescriptor indexDescriptor) {
    this(indexDescriptor, null);
  }

  public AuthorizationFilterTransformer(
      final IndexDescriptor indexDescriptor, final ResourceAccessFilter resourceAccessManager) {
    super(indexDescriptor, resourceAccessManager);
  }

  @Override
  public AuthorizationFilterTransformer withResourceAccessFilter(
      final ResourceAccessFilter resourceAccessFilter) {
    return new AuthorizationFilterTransformer(indexDescriptor, resourceAccessFilter);
  }

  @Override
  protected SearchQuery toAuthorizationSearchQuery(final Authorization authorization) {
    final var resourceIds = authorization.resourceIds();
    return stringTerms(ID, resourceIds);
  }

  @Override
  protected SearchQuery toTenantSearchQuery(final List<String> tenantIds) {
    // Authorizations are not tenant-owned => no tenant check required
    return matchAll();
  }

  @Override
  public SearchQuery toSearchQuery(final AuthorizationFilter filter) {
    if (filter.ownerTypeToOwnerIds() != null && !filter.ownerTypeToOwnerIds().isEmpty()) {
      return buildOwnerTypeToOwnerIdsQuery(filter);
    }

    return and(
        buildCoreFilters(filter),
        stringTerms(OWNER_ID, filter.ownerIds()),
        filter.ownerType() == null ? null : term(OWNER_TYPE, filter.ownerType()));
  }

  private SearchQuery buildOwnerTypeToOwnerIdsQuery(final AuthorizationFilter filter) {
    return or(
        filter.ownerTypeToOwnerIds().entrySet().stream()
            .map(
                entry ->
                    and(
                        buildCoreFilters(filter),
                        term(OWNER_TYPE, entry.getKey().name()),
                        stringTerms(OWNER_ID, entry.getValue())))
            .toList());
  }

  private SearchQuery buildCoreFilters(final AuthorizationFilter filter) {
    return and(
        filter.authorizationKey() == null ? null : term(ID, filter.authorizationKey()),
        stringTerms(RESOURCE_ID, filter.resourceIds()),
        filter.resourceType() == null ? null : term(RESOURCE_TYPE, filter.resourceType()),
        filter.permissionTypes() == null
            ? null
            : stringTerms(
                PERMISSIONS_TYPES, filter.permissionTypes().stream().map(Enum::name).toList()));
  }
}
