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
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.PERMISSIONS_TYPES;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_MATCHER;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_PROPERTY_NAME;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuthorizationFilterTransformer
    extends IndexFilterTransformer<AuthorizationFilter> {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationFilterTransformer.class);

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

  /**
   * For the {@code AUTHORIZATION} resource, a resource ID is an authorization key. A user can still
   * be granted the resource with any other ID, such as a resource type name like {@code GROUP}, but
   * that can never match a key, so it grants nothing: such IDs are skipped instead of failing the
   * search, and if none is left the search matches nothing.
   */
  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    final List<Long> authorizationKeys = new ArrayList<>();
    final List<String> skippedResourceIds = new ArrayList<>();
    for (final String resourceId : authorization.resourceIds()) {
      final Long authorizationKey = toAuthorizationKey(resourceId);
      if (authorizationKey == null) {
        skippedResourceIds.add(resourceId);
      } else {
        authorizationKeys.add(authorizationKey);
      }
    }
    // DEBUG: this runs on every search of an affected user, and a skipped ID grants nothing anyway
    if (!skippedResourceIds.isEmpty()) {
      LOG.debug(
          "Skipping AUTHORIZATION resource IDs that are not authorization keys, so grant nothing: {}",
          skippedResourceIds);
    }
    return authorizationKeys.isEmpty() ? matchNone() : longTerms(ID, authorizationKeys);
  }

  private static Long toAuthorizationKey(final String resourceId) {
    try {
      return Long.valueOf(resourceId);
    } catch (final NumberFormatException notAnAuthorizationKey) {
      return null;
    }
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
        stringTerms(RESOURCE_PROPERTY_NAME, filter.resourcePropertyNames()),
        filter.resourceMatcher() == null ? null : term(RESOURCE_MATCHER, filter.resourceMatcher()),
        filter.resourceType() == null ? null : term(RESOURCE_TYPE, filter.resourceType()),
        filter.permissionTypes() == null
            ? null
            : stringTerms(
                PERMISSIONS_TYPES, filter.permissionTypes().stream().map(Enum::name).toList()));
  }
}
