/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.executor;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.auth.AuthorizationQueryTransformer;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;

public class SearchQueryAuthorizationApplier implements SearchQueryRequestInterceptor {

  private final SecurityContext securityContext;
  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationQueryTransformer transformer;

  public SearchQueryAuthorizationApplier(
      final SecurityContext securityContext,
      final AuthorizationChecker authorizationChecker,
      final AuthorizationQueryTransformer transformer) {
    this.securityContext = securityContext;
    this.authorizationChecker = authorizationChecker;
    this.transformer = transformer;
  }

  @Override
  public SearchQueryRequest apply(final SearchQueryRequest searchQueryRequest) {
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
              transformer.toSearchQuery(resourceType, permissionType, resourceKeys));
    }
    return searchQueryRequest.toBuilder().query(authorizedQuery).build();
  }
}
