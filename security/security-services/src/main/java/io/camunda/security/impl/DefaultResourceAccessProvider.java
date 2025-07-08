/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.security.auth.Authorization.WILDCARD;
import static io.camunda.security.auth.Authorization.with;

import io.camunda.search.clients.security.ResourceAccess;
import io.camunda.search.clients.security.ResourceAccessProvider;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.List;
import java.util.Optional;

public class DefaultResourceAccessProvider implements ResourceAccessProvider {

  private final AuthorizationChecker authorizationChecker;

  public DefaultResourceAccessProvider(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
  }

  @Override
  public ResourceAccess resolveResourcesAccess(
      final CamundaAuthentication authentication, final Authorization<?> requiredAuthorization) {
    final var authorizationBuilder =
        Optional.ofNullable(requiredAuthorization)
            .map(
                a ->
                    new Authorization.Builder<>()
                        .resourceType(requiredAuthorization.resourceType())
                        .permissionType(requiredAuthorization.permissionType()))
            .orElseThrow(
                () ->
                    new CamundaSearchException(
                        "Required authorization to resolve resource access, but no authorizations were provided"));

    if (isUnspecifiedAuthorization(requiredAuthorization)) {
      return ResourceAccess.wildcard(authorizationBuilder.resourceId(WILDCARD).build());
    }

    // fetch the authorization entities for the authenticated user
    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var resourceIds = authorizationChecker.retrieveAuthorizedResourceKeys(securityContext);

    if (resourceIds.contains(WILDCARD)) {
      // no authorization check required, user can access
      // the respective resources.
      return ResourceAccess.wildcard(authorizationBuilder.resourceId(WILDCARD).build());
    }

    if (resourceIds.isEmpty()) {
      return ResourceAccess.denied(authorizationBuilder.build());
    }

    final var authorizationWithResolvedResourceIds =
        authorizationBuilder.resourceIds(resourceIds).build();
    return ResourceAccess.granted(authorizationWithResolvedResourceIds);
  }

  @Override
  public <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {
    final var resourceIdSupplier = requiredAuthorization.resourceIdSupplier();
    final var resourceIds = requiredAuthorization.resourceIds();

    if (isUnspecifiedAuthorization(requiredAuthorization)) {
      return ResourceAccess.wildcard(with(requiredAuthorization));
    }

    final var resourceId =
        Optional.ofNullable(resourceIdSupplier)
            .map(supplier -> supplier.apply(resource))
            .orElseGet(
                () ->
                    Optional.ofNullable(resourceIds)
                        .filter(l -> l.size() == 1)
                        .map(List::getFirst)
                        .orElseThrow(
                            () ->
                                new CamundaSearchException(
                                    "Expected one resource id to ensure resource access")));

    return hasResourceAccess(authentication, requiredAuthorization, resourceId);
  }

  @Override
  public ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<?> requiredAuthorization,
      final String resourceId) {
    if (isUnspecifiedAuthorization(requiredAuthorization)) {
      return ResourceAccess.wildcard(with(requiredAuthorization));
    }

    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var isAuthorized = authorizationChecker.isAuthorized(resourceId, securityContext);
    final var checkedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(requiredAuthorization.resourceType())
                    .permissionType(requiredAuthorization.permissionType())
                    .resourceIds(List.of(resourceId)));

    return isAuthorized
        ? ResourceAccess.granted(checkedAuthorization)
        : ResourceAccess.denied(checkedAuthorization);
  }

  private SecurityContext createSecurityContext(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return SecurityContext.of(
        s -> s.withAuthentication(authentication).withAuthorization(authorization));
  }

  private boolean isUnspecifiedAuthorization(final Authorization<?> authorization) {
    return AuthorizationResourceType.UNSPECIFIED.equals(authorization.resourceType());
  }
}
