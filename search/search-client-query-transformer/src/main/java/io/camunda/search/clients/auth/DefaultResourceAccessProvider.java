/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.AuthorizationScope.AuthorizationScopeFactory;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;

/**
 * Document based datastore (ES/OS) strategy implementation of {@link ResourceAccessProvider}. It
 * applies authorization to a search query by fetching the authorized resources for the
 * authenticated user and creating a new search query with the authorization applied.
 */
public class DefaultResourceAccessProvider implements ResourceAccessProvider {

  private final AuthorizationChecker authorizationChecker;

  public DefaultResourceAccessProvider(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
  }

  @Override
  public ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization requiredAuthorization) {
    // right now, not all Services provide an authorization with #withSecurityContext()
    // typically, the authorization check happens afterward in the respective Service
    if (requiredAuthorization == null) {
      return ResourceAccess.wildcard(null);
    }

    final var resultingAuthorization =
        new Authorization.Builder()
            .resourceType(requiredAuthorization.resourceType())
            .permissionType(requiredAuthorization.permissionType());

    // fetch the authorization entities for the authenticated user
    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var resourceIds = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceIds.contains(AuthorizationScopeFactory.wildcard())) {
      // no authorization check required, user can access
      // the respective resources.
      return ResourceAccess.wildcard(resultingAuthorization.resourceId(WILDCARD).build());
    }

    if (resourceIds.isEmpty()) {
      return ResourceAccess.denied(resultingAuthorization.build());
    }

    final var authorizationWithResolvedResourceIds =
        resultingAuthorization.authorizationScopes(resourceIds).build();
    return ResourceAccess.allowed(authorizationWithResolvedResourceIds);
  }

  private SecurityContext createSecurityContext(
      final CamundaAuthentication authentication, final Authorization authorization) {
    return SecurityContext.of(
        s -> s.withAuthentication(authentication).withAuthorization(authorization));
  }
}
