/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.control.ResourceAccessControl;
import io.camunda.search.clients.control.ResourceAccessControl.ResourceAccess;
import io.camunda.search.clients.control.ResourceAccessControl.TenantAccess;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.auth.AuthorizationQueryTransformers;
import io.camunda.search.query.SearchQueryBase;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;

/**
 * Document based datastore (ES/OS) strategy implementation of {@link AuthorizationQueryStrategy}.
 * It applies authorization to a search query by fetching the authorized resources for the
 * authenticated user and creating a new search query with the authorization applied.
 */
public class DocumentAuthorizationQueryStrategy implements AuthorizationQueryStrategy {

  private final AuthorizationChecker authorizationChecker;

  public DocumentAuthorizationQueryStrategy(final AuthorizationChecker authorizationSearchClient) {
    authorizationChecker = authorizationSearchClient;
  }

  @Override
  public SearchQueryRequest applyAuthorizationToQuery(
      final SearchQueryRequest searchQueryRequest,
      final SecurityContext securityContext,
      final Class<? extends SearchQueryBase> queryClass) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return searchQueryRequest;
    }
    // fetch the authorization entities for the authenticated user
    final var resourceKeys = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceKeys.contains(WILDCARD)) {
      return searchQueryRequest;
    }

    // create a new search query request with the authorization applied
    final SearchQuery authorizedQuery;
    if (resourceKeys.isEmpty()) {
      authorizedQuery = matchNone();
    } else {
      final var resourceType = securityContext.authorization().resourceType();
      final var permissionType = securityContext.authorization().permissionType();
      authorizedQuery =
          and(
              searchQueryRequest.query(),
              AuthorizationQueryTransformers.getTransformer(queryClass)
                  .toSearchQuery(resourceType, permissionType, resourceKeys));
    }
    return searchQueryRequest.toBuilder().query(authorizedQuery).build();
  }

  @Override
  public ResourceAccessControl determineResourceAccessControl(
      final SecurityContext securityContext) {
    final var resourceAccess = determineResourceAccess(securityContext);
    final var tenantAccess = determineTenantAccess(securityContext);
    return ResourceAccessControl.of(
        b -> b.resourceAccess(resourceAccess).tenantAccess(tenantAccess));
  }

  @Override
  public boolean canAccessResource(final String resourceId, final SecurityContext securityContext) {
    return authorizationChecker.isAuthorized(resourceId, securityContext);
  }

  protected ResourceAccess determineResourceAccess(final SecurityContext securityContext) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return ResourceAccess.successful();
    }

    // fetch the authorization entities for the authenticated user
    final var resourceKeys = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceKeys.contains(WILDCARD)) {
      return ResourceAccess.successful();
    }

    if (resourceKeys.isEmpty()) {
      return ResourceAccess.unsuccessful();
    }

    final var givenAuthorization = securityContext.authorization();
    final var requiredAuthorizationCheck =
        new Authorization(
            givenAuthorization.resourceType(), givenAuthorization.permissionType(), resourceKeys);
    return ResourceAccess.required(requiredAuthorizationCheck);
  }

  protected TenantAccess determineTenantAccess(final SecurityContext securityContext) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return TenantAccess.successful();
    }

    final var authentication = securityContext.authentication();
    final var tenantIds = authentication.authenticatedTenantIds();

    if (tenantIds.isEmpty()) {
      return TenantAccess.unsuccessful();
    }

    return TenantAccess.required(tenantIds);
  }
}
