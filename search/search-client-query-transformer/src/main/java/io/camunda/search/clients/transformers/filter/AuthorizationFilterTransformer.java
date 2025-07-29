/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.PERMISSIONS_TYPES;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_MATCHER;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public final class AuthorizationFilterTransformer
    extends IndexFilterTransformer<AuthorizationFilter> {

  public AuthorizationFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AuthorizationFilter filter) {
    return and(
        buildCoreFilters(filter),
        filter.ownerTypeToOwnerIds() != null ? buildOwnerTypeToOwnerIdsQuery(filter) : null,
        stringTerms(OWNER_ID, filter.ownerIds()),
        filter.ownerType() == null ? null : term(OWNER_TYPE, filter.ownerType()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return longTerms(ID, authorization.resourceIds().stream().map(Long::valueOf).toList());
  }

  private SearchQuery buildOwnerTypeToOwnerIdsQuery(final AuthorizationFilter filter) {
    return or(
        filter.ownerTypeToOwnerIds().entrySet().stream()
            .map(
                entry -> {
                  final var key = entry.getKey();
                  final var value = entry.getValue();

                  if (value == null || value.isEmpty()) {
                    final var message =
                        "Cannot build owner type to owner ids query, because value for owner type '%s' is null or empty."
                            .formatted(key.name());
                    throw new CamundaSearchException(message);
                  }

                  return and(term(OWNER_TYPE, key.name()), stringTerms(OWNER_ID, value));
                })
            .toList());
  }

  private SearchQuery buildCoreFilters(final AuthorizationFilter filter) {
    return and(
        filter.authorizationKey() == null ? null : term(ID, filter.authorizationKey()),
        stringTerms(RESOURCE_ID, filter.resourceIds()),
        filter.resourceMatcher() == null ? null : term(RESOURCE_MATCHER, filter.resourceMatcher()),
        filter.resourceType() == null ? null : term(RESOURCE_TYPE, filter.resourceType()),
        filter.permissionTypes() == null
            ? null
            : stringTerms(
                PERMISSIONS_TYPES, filter.permissionTypes().stream().map(Enum::name).toList()));
  }
}
