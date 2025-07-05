/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.security.policy;

import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.resource.AuthorizationBasedResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessFilter;
import io.camunda.security.resource.ResourceAccessPolicy;
import io.camunda.security.resource.TenantBasedResourceAccessFilter;
import java.util.Optional;

/**
 * Document-based datastore (ES/OS) policy implementation of {@link ResourceAccessPolicy}. It
 * fetches the authorized resources for the authenticated user and returns an instance of {@link
 * ResourceAccessFilter}.
 */
public class SearchQueryBasedResourceAccessPolicy implements ResourceAccessPolicy {

  private final AuthorizationChecker authorizationChecker;

  public SearchQueryBasedResourceAccessPolicy(
      final AuthorizationSearchClient authorizationSearchClient) {
    authorizationChecker = new AuthorizationChecker(authorizationSearchClient);
  }

  @Override
  public ResourceAccessFilter applySecurityContext(final SecurityContext securityContext) {
    final var authorizationFilter = applySecurityContextToAuthorizationFilter(securityContext);
    final var tenantFilter = applySecurityContextToTenantFilter(securityContext);
    return ResourceAccessFilter.of(
        b -> b.authorizationFilter(authorizationFilter).tenantFilter(tenantFilter));
  }

  protected AuthorizationBasedResourceAccessFilter applySecurityContextToAuthorizationFilter(
      final SecurityContext securityContext) {
    if (!securityContext.requiresAuthorizationChecks()) {
      return AuthorizationBasedResourceAccessFilter.successful();
    }

    // fetch the authorization entities for the authenticated user
    final var resourceIds = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceIds.contains(WILDCARD)) {
      return AuthorizationBasedResourceAccessFilter.successful();
    }

    if (resourceIds.isEmpty()) {
      return AuthorizationBasedResourceAccessFilter.unsuccessful();
    }

    final var givenAuthorization = securityContext.authorization();
    final var givenResourceType = givenAuthorization.resourceType();
    final var givenPermissionType = givenAuthorization.permissionType();

    final var requiredAuthorizationCheck =
        Authorization.of(
            b ->
                b.resourceType(givenResourceType)
                    .permissionType(givenPermissionType)
                    .resourceIds(resourceIds));
    return AuthorizationBasedResourceAccessFilter.requiredAuthorizationCheck(
        requiredAuthorizationCheck);
  }

  protected TenantBasedResourceAccessFilter applySecurityContextToTenantFilter(
      final SecurityContext securityContext) {
    final boolean shouldCheckTenant =
        Optional.ofNullable(securityContext.authentication())
            .map(CamundaAuthentication::authenticatedTenantIds)
            .filter(tenantIds -> !tenantIds.isEmpty())
            .isPresent();

    final var authentication = securityContext.authentication();
    if (!shouldCheckTenant) {
      return TenantBasedResourceAccessFilter.successful();
    }

    final var tenantIds = authentication.authenticatedTenantIds();
    return TenantBasedResourceAccessFilter.tenantCheckRequired(tenantIds);
  }
}
