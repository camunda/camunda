/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security.policy;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.resource.AuthorizationResult;
import io.camunda.security.resource.ResourceAccessPolicy;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.security.resource.TenantResult;
import java.util.Optional;

public class SearchGetBasedResourceAccessPolicy<D> implements ResourceAccessPolicy<D> {

  private final AuthorizationChecker authorizationChecker;
  private final AuthorizationSearchClient authorizationSearchClient;
  private final D resource;

  public SearchGetBasedResourceAccessPolicy(
      final AuthorizationSearchClient authorizationSearchClient) {
    this(authorizationSearchClient, null);
  }

  public SearchGetBasedResourceAccessPolicy(
      final AuthorizationSearchClient authorizationSearchClient, final D resource) {
    authorizationChecker = new AuthorizationChecker(authorizationSearchClient);
    this.authorizationSearchClient = authorizationSearchClient;
    this.resource = resource;
  }

  @Override
  public ResourceAccessResult applySecurityContext(final SecurityContext securityContext) {
    final var authorizationFilter = applySecurityContextToAuthorizationFilter(securityContext);
    final var tenantFilter = applySecurityContextToTenantFilter(securityContext);
    return ResourceAccessResult.of(
        b -> b.authorizationResult(authorizationFilter).tenantResult(tenantFilter));
  }

  @Override
  public ResourceAccessPolicy<D> withResource(final D resource) {
    return new SearchGetBasedResourceAccessPolicy<>(authorizationSearchClient, resource);
  }

  protected AuthorizationResult applySecurityContextToAuthorizationFilter(
      final SecurityContext securityContext) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return AuthorizationResult.successful();
    }

    final var authorization = (Authorization<D>) securityContext.authorization();
    final var givenResourceIds = authorization.resourceIds();
    final var givenResourceIdsSupplier = authorization.resourceIdsSupplier();

    final var resourceIds =
        Optional.ofNullable(givenResourceIds)
            .filter(p -> !p.isEmpty())
            .orElseGet(() -> givenResourceIdsSupplier.apply(resource));

    final var allowed = authorizationChecker.isAuthorized(resourceIds.getFirst(), securityContext);

    if (allowed) {
      return AuthorizationResult.successful();
    } else {
      return AuthorizationResult.unsuccessful();
    }
  }

  protected TenantResult applySecurityContextToTenantFilter(final SecurityContext securityContext) {
    return TenantResult.successful();
  }
}
