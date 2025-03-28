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
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    final var ownerIds = collectOwnerIds(securityContext.authentication());
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    final var authorizationEntities =
        authorizationSearchClient.findAllAuthorizations(
            AuthorizationQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.ownerIds(ownerIds)
                                .resourceType(resourceType.name())
                                .permissionTypes(permissionType))));
    return authorizationEntities.stream()
        .filter(e -> e.permissionTypes().contains(permissionType))
        .map(AuthorizationEntity::resourceId)
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
    final var ownerIds = collectOwnerIds(securityContext.authentication());
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    return authorizationSearchClient
            .searchAuthorizations(
                AuthorizationQuery.of(
                    q ->
                        q.filter(
                                f ->
                                    f.ownerIds(ownerIds)
                                        .resourceType(resourceType.name())
                                        .permissionTypes(permissionType)
                                        .resourceIds(List.of(WILDCARD, resourceId)))
                            .page(p -> p.size(1))))
            .total()
        > 0;
  }

  /**
   * Collects the permission types available for a resource, defined by resource id and resource
   * type
   *
   * @param resourceId the resource id to return permission types for
   * @param resourceType the resource type to return permission types for
   * @param authentication the authentication information
   * @return the permission types found
   */
  public Set<PermissionType> collectPermissionTypes(
      final String resourceId,
      final AuthorizationResourceType resourceType,
      final Authentication authentication) {
    final var ownerIds = collectOwnerIds(authentication);
    final var authorizationEntities =
        authorizationSearchClient.findAllAuthorizations(
            AuthorizationQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.ownerIds(ownerIds)
                                .resourceType(resourceType.name())
                                .resourceIds(List.of(WILDCARD, resourceId)))));

    return collectPermissionTypes(authorizationEntities);
  }

  private Set<PermissionType> collectPermissionTypes(
      final List<AuthorizationEntity> authorizationEntities) {
    return authorizationEntities.stream()
        .flatMap(a -> a.permissionTypes().stream())
        .collect(Collectors.toSet());
  }

  private List<String> collectOwnerIds(final Authentication authentication) {
    final List<String> ownerIds = new ArrayList<>();
    ownerIds.add(authentication.authenticatedUsername());
    ownerIds.addAll(authentication.authenticatedMappingIds());
    ownerIds.addAll(
        // TODO remove this mapping when refactoring Groups to IDs
        authentication.authenticatedGroupKeys().stream()
            .map(Object::toString)
            .collect(Collectors.toSet()));
    ownerIds.addAll(
        // TODO remove this mapping when refactoring Roles to IDs
        authentication.authenticatedRoleKeys().stream()
            .map(Object::toString)
            .collect(Collectors.toSet()));
    return ownerIds;
  }
}
