/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The AuthorizationChecker class provides methods for checking resource authorization by
 * interacting with the AuthorizationSearchClient. It retrieves authorized resource keys or checks
 * if a specific resource key is authorized, based on the provided SecurityContext.
 */
public class AuthorizationChecker {

  private final AuthorizationSearchClient authorizationSearchClient;

  public AuthorizationChecker(final AuthorizationSearchClient authorizationSearchClient) {
    this.authorizationSearchClient =
        authorizationSearchClient.withSecurityContext(SecurityContext.withoutAuthentication());
  }

  /**
   * Retrieves a list of authorized resource keys for the given SecurityContext. The resource keys
   * represent resources that the user or one of their groups or roles, as specified in the
   * SecurityContext, has access to based on the defined resource type and permission type.
   *
   * @param securityContext the context containing authorization and authentication information
   * @return a list of authorized resource keys for the user or group in the SecurityContext
   */
  public List<String> retrieveAuthorizedResourceKeys(final SecurityContext securityContext) {
    final var ownerKeys = collectOwnerKeys(securityContext.authentication());
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    final var authorizationEntities =
        authorizationSearchClient.findAllAuthorizations(
            AuthorizationQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.ownerKeys(ownerKeys)
                                .resourceType(resourceType.name())
                                .permissionType(permissionType))));
    return authorizationEntities.stream()
        .flatMap(
            e ->
                e.permissions().stream()
                    .filter(permission -> permissionType.equals(permission.type()))
                    .flatMap(permission -> permission.resourceIds().stream()))
        .toList();
  }

  /**
   * Checks if a specific resource key is authorized for the user or one of their groups or roles
   * defined in the provided SecurityContext. The authorization check is based on the resource type
   * and permission type in the SecurityContext.
   *
   * @param resourceId the resource id to check authorization for
   * @param securityContext the context containing authorization and authentication information
   * @return true if the resource key is authorized, false otherwise
   */
  public boolean isAuthorized(final String resourceId, final SecurityContext securityContext) {
    final var ownerKeys = collectOwnerKeys(securityContext.authentication());
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    return authorizationSearchClient
            .searchAuthorizations(
                AuthorizationQuery.of(
                    q ->
                        q.filter(
                                f ->
                                    f.ownerKeys(ownerKeys)
                                        .resourceType(resourceType.name())
                                        .permissionType(permissionType)
                                        .resourceIds(List.of(WILDCARD, resourceId)))
                            .page(p -> p.size(1))))
            .total()
        > 0;
  }

  private List<Long> collectOwnerKeys(final Authentication authentication) {
    final List<Long> ownerKeys = new ArrayList<>();
    ownerKeys.add(authentication.authenticatedUserKey());
    if (authentication.authenticatedGroupKeys() != null) {
      ownerKeys.addAll(authentication.authenticatedGroupKeys());
    }
    return ownerKeys;
  }
}