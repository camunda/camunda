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
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultResourceAccessProvider implements ResourceAccessProvider {

  private final AuthorizationChecker authorizationChecker;

  public DefaultResourceAccessProvider(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
  }

  @Override
  public <T> ResourceAccess resolveResourceAccess(
      final CamundaAuthentication authentication, final Authorization<T> authorization) {
    if (authorization.hasAnyResourcePropertyNames()) {
      return resolveResourceAccessByPropertyNames(authentication, authorization);
    }

    return resolveResourceAccessByResourceId(authentication, authorization);
  }

  private <T> ResourceAccess resolveResourceAccessByResourceId(
      final CamundaAuthentication authentication, final Authorization<T> authorization) {

    // fetch the authorization entities for the authenticated user
    final var authorizationScopes =
        authorizationChecker.retrieveAuthorizedAuthorizationScopes(authentication, authorization);

    if (authorizationScopes.isEmpty()) {
      return ResourceAccess.denied(authorization);
    }

    if (authorizationScopes.contains(WILDCARD)) {
      // no authorization check required, user can access
      // the respective resources.
      return ResourceAccess.wildcard(authorization.with(WILDCARD.getResourceId()));
    }

    final var authorizedResourceIds =
        authorizationScopes.stream()
            .filter(scope -> scope.getMatcher() == AuthorizationResourceMatcher.ID)
            .map(AuthorizationScope::getResourceId)
            .distinct()
            .toList();

    if (authorizedResourceIds.isEmpty()) {
      return ResourceAccess.denied(authorization);
    }

    return ResourceAccess.allowed(authorization.withResourceIds(authorizedResourceIds));
  }

  private <T> ResourceAccess resolveResourceAccessByPropertyNames(
      final CamundaAuthentication authentication, final Authorization<T> authorization) {

    final var authorizedResourcePropertyNames =
        authorizationChecker
            .retrieveAuthorizedAuthorizationScopes(authentication, authorization)
            .stream()
            .filter(scope -> scope.getMatcher() == AuthorizationResourceMatcher.PROPERTY)
            .map(AuthorizationScope::getResourcePropertyName)
            .collect(Collectors.toSet());

    final var resolvedResourcePropertyNames =
        authorization.resourcePropertyNames().stream()
            .filter(authorizedResourcePropertyNames::contains)
            .collect(Collectors.toSet());
    final var resolvedAuthorization =
        authorization.withResourcePropertyNames(resolvedResourcePropertyNames);

    if (resolvedResourcePropertyNames.isEmpty()) {
      return ResourceAccess.denied(resolvedAuthorization);
    }

    return ResourceAccess.allowed(resolvedAuthorization);
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
    final var isAuthorized =
        authorizationChecker.isAuthorized(
            AuthorizationScope.of(resourceId), authentication, requiredAuthorization);
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
}
