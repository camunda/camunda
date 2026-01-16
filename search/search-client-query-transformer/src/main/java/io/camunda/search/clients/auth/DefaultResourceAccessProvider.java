/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD;

import io.camunda.search.clients.auth.matcher.ResourcePropertyMatcherRegistry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultResourceAccessProvider implements ResourceAccessProvider {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultResourceAccessProvider.class);

  private final AuthorizationChecker authorizationChecker;
  private final ResourcePropertyMatcherRegistry propertyMatcherRegistry;

  public DefaultResourceAccessProvider(final AuthorizationChecker authorizationChecker) {
    this.authorizationChecker = authorizationChecker;
    this.propertyMatcherRegistry = new ResourcePropertyMatcherRegistry();
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
    if (requiredAuthorization.hasAnyResourcePropertyNames()) {
      return hasResourceAccessByProperties(authentication, requiredAuthorization, resource);
    }

    final var resourceId = resolveResourceId(requiredAuthorization, resource);
    return hasResourceAccessByResourceId(authentication, requiredAuthorization, resourceId);
  }

  private <T> ResourceAccess hasResourceAccessByProperties(
      final CamundaAuthentication authentication,
      final Authorization<T> requiredAuthorization,
      final T resource) {
    // resolve which properties the user is authorized for
    final var resolvedAccess =
        resolveResourceAccessByPropertyNames(authentication, requiredAuthorization);

    if (resolvedAccess.denied()) {
      return resolvedAccess;
    }

    final var authorizedPropertyNames = resolvedAccess.authorization().resourcePropertyNames();
    final var matcher = propertyMatcherRegistry.getMatcher(resource);

    if (matcher.isEmpty()) {
      LOG.warn(
          "No property matcher found for resource type '{}'; denying access.",
          resource.getClass().getSimpleName());
      return ResourceAccess.denied(resolvedAccess.authorization());
    }

    final var matches = matcher.get().matches(resource, authorizedPropertyNames, authentication);
    if (matches) {
      return ResourceAccess.allowed(resolvedAccess.authorization());
    }

    return ResourceAccess.denied(resolvedAccess.authorization());
  }

  private <T> String resolveResourceId(
      final Authorization<T> requiredAuthorization, final T resource) {
    final var resourceIdSupplier = requiredAuthorization.resourceIdSupplier();
    final var resourceIds = requiredAuthorization.resourceIds();
    return Optional.ofNullable(resourceIdSupplier)
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
