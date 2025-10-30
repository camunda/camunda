/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DefaultResourceAccessProvider implements ResourceAccessProvider {

  private final AuthorizationChecker authorizationChecker;

  public DefaultResourceAccessProvider(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
  }

  @Override
  public <T> ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<T> requiredAuthorization) {

    if (requiredAuthorization.resourceIds() != null
        && !requiredAuthorization.resourceIds().isEmpty()) {
      return resolveResourceAccessByResourceId(authentication, requiredAuthorization);
    } else if (requiredAuthorization.propertyName() != null) {
      return resolveResourceAccessByProperty(authentication, requiredAuthorization);
    }

    throw new IllegalArgumentException();
  }

  @Override
  public <T> ResourceAccess hasResourceAccess(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {
    final var resourceIdSupplier = requiredAuthorization.resourceIdSupplier();
    final var resourceIds = requiredAuthorization.resourceIds();
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
                                    "Expected one resource id to check resource access, but received none or more than one")));

    return hasResourceAccessByResourceId(authentication, requiredAuthorization, resourceId);
  }

  @Override
  public <T> ResourceAccess hasResourceAccessByResourceId(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final String resourceId) {
    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var isAuthorized =
        authorizationChecker.isAuthorized(AuthorizationScope.of(resourceId), securityContext);
    final var checkedAuthorization =
        Authorization.of(
            a ->
                a.resourceType(requiredAuthorization.resourceType())
                    .permissionType(requiredAuthorization.permissionType())
                    .resourceIds(List.of(resourceId)));

    return isAuthorized
        ? ResourceAccess.allowed(checkedAuthorization)
        : ResourceAccess.denied(checkedAuthorization);
  }

  public <T> ResourceAccess hasResourceAccessByProperties(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {

    final PropertyMatcher<T, CamundaAuthentication> matcher = requiredAuthorization.propertyMatcher();
    final Map<String, Boolean> matched = matcher.match(resource, authentication);

    if (matched.isEmpty()) {
      return ResourceAccess.denied(checkedAuthorization):
    }

    for (Map.Entry<String, Boolean> entry : matched.entrySet()) {
      final var isAuthorized = authorizationChecker.isAuthorized(...);
      if (isAuthorized) {
        return ResourceAccess.allowed(...);
      }
    }

  }

  public <T> ResourceAccess resolveResourceAccessByResourceId(
      final CamundaAuthentication authentication, final Authorization<T> requiredAuthorization) {
    final var resultingAuthorization =
        new Authorization.Builder<>()
            .resourceType(requiredAuthorization.resourceType())
            .permissionType(requiredAuthorization.permissionType());

    // fetch the authorization entities for the authenticated user
    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var authorizationScopes =
        authorizationChecker.retrieveAuthorizedAuthorizationScopes(securityContext);

    if (authorizationScopes.contains(WILDCARD)) {
      // no authorization check required, user can access
      // the respective resources.
      return ResourceAccess.wildcard(
          resultingAuthorization.resourceId(WILDCARD.getResourceId()).build());
    }

    if (authorizationScopes.isEmpty()) {
      return ResourceAccess.denied(resultingAuthorization.build());
    }

    final var resourceIds =
        authorizationScopes.stream().map(AuthorizationScope::getResourceId).distinct().toList();
    final var authorizationWithResolvedResourceIds =
        resultingAuthorization.resourceIds(resourceIds).build();
    return ResourceAccess.allowed(authorizationWithResolvedResourceIds);
  }

  public <T> ResourceAccess resolveResourceAccessByProperty(
      final CamundaAuthentication authentication, final Authorization<T> requiredAuthorization) {
    final var resultingAuthorization =
        new Authorization.Builder<>()
            .resourceType(requiredAuthorization.resourceType())
            .permissionType(requiredAuthorization.permissionType());

    // fetch the authorization entities for the authenticated user
    final var securityContext = createSecurityContext(authentication, requiredAuthorization);
    final var authorizationScopes =
        authorizationChecker.retrieveAuthorizedAuthorizationPropertyScopes(securityContext);

    if (authorizationScopes.isEmpty()) {
      return ResourceAccess.denied(resultingAuthorization.build());
    }

    final var propertyNames =
        authorizationScopes.stream().map(AuthorizationScope::getPropertyName).distinct().toList();

    final var requiredPropertyName = requiredAuthorization.propertyName();
    // TODO: intersection: requiredAuthorization.propertyNames().intersect(propertyNames) =>
    // if empty => then access denied
    // if not empty return only the intersection
    if (propertyNames.contains(requiredPropertyName)) {
      return ResourceAccess.allowed(
          resultingAuthorization
              .propertyName(requiredPropertyName)
              .propertyValues(requiredAuthorization.propertyValuesSupplier().apply(authentication))
              .build());
    } else {
      return ResourceAccess.denied(resultingAuthorization.build());
    }
  }

  private SecurityContext createSecurityContext(
      final CamundaAuthentication authentication, final Authorization<?> authorization) {
    return SecurityContext.of(
        s -> s.withAuthentication(authentication).withAuthorization(authorization));
  }
}
