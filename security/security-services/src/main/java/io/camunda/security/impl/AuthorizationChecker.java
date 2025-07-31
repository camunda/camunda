/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import static io.camunda.security.auth.Authorization.WILDCARD;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The AuthorizationChecker class provides methods for checking resource authorization by
 * interacting with the AuthorizationSearchClient. It retrieves authorized resource ids or checks if
 * a specific resource id is authorized, based on the provided SecurityContext.
 */
public class AuthorizationChecker {

  private final AuthorizationReader authorizationReader;

  public AuthorizationChecker(final AuthorizationReader authorizationReader) {
    this.authorizationReader = authorizationReader;
  }

  /**
   * Retrieves a list of authorized resource ids for the given SecurityContext. The resource ids
   * represent resources that the user or one of their groups or roles, as specified in the
   * SecurityContext, has access to based on the defined resource type and permission type.
   *
   * @param securityContext the context containing authorization and authentication information
   * @return a list of authorized resource ids for the user or group in the SecurityContext
   */
  public List<String> retrieveAuthorizedResourceIds(final SecurityContext securityContext) {
    final var authentication = securityContext.authentication();
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    return getOrElseDefaultResult(
        authentication,
        (ownerIds) -> {
          final var query =
              AuthorizationQuery.of(
                  q ->
                      q.filter(
                              f ->
                                  f.ownerTypeToOwnerIds(ownerIds)
                                      .resourceType(resourceType.name())
                                      .permissionTypes(permissionType))
                          .unlimited());
          return authorizationReader.search(query, ResourceAccessChecks.disabled()).items().stream()
              .filter(e -> e.permissionTypes().contains(permissionType))
              .map(AuthorizationEntity::resourceId)
              .toList();
        },
        List::of);
  }

  /**
   * Checks if a specific resource id is authorized for the user or one of their groups or roles
   * defined in the provided SecurityContext. The authorization check is based on the resource type
   * and permission type in the SecurityContext.
   *
   * @param resourceId the resource id to check authorization for
   * @param securityContext the context containing authorization and authentication information
   * @return true if the resource id is authorized, false otherwise
   */
  public boolean isAuthorized(final String resourceId, final SecurityContext securityContext) {
    final var authentication = securityContext.authentication();
    final var resourceType = securityContext.authorization().resourceType();
    final var permissionType = securityContext.authorization().permissionType();
    return getOrElseDefaultResult(
        authentication,
        (ownerIds) -> {
          final var query =
              AuthorizationQuery.of(
                  q ->
                      q.filter(
                              f ->
                                  f.ownerTypeToOwnerIds(ownerIds)
                                      .resourceType(resourceType.name())
                                      .permissionTypes(permissionType)
                                      .resourceIds(
                                          List.of(
                                              AuthorizationScope.WILDCARD.getResourceId(),
                                              authorizationScope.getResourceId())))
                          .page(p -> p.size(1)));
          return authorizationReader.search(query, ResourceAccessChecks.disabled()).total() > 0;
        },
        () -> false);
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
      final CamundaAuthentication authentication) {
    return getOrElseDefaultResult(
        authentication,
        (ownerIds) -> {
          final var query =
              AuthorizationQuery.of(
                  q ->
                      q.filter(
                              f ->
                                  f.ownerTypeToOwnerIds(ownerIds)
                                      .resourceType(resourceType.name())
                                      .resourceIds(
                                          List.of(
                                              AuthorizationScope.WILDCARD.getResourceId(),
                                              resourceId)))
                          .unlimited());
          final var authorizationEntities =
              authorizationReader.search(query, ResourceAccessChecks.disabled()).items();
          return collectPermissionTypes(authorizationEntities);
        },
        Set::of);
  }

  private Set<PermissionType> collectPermissionTypes(
      final List<AuthorizationEntity> authorizationEntities) {
    return authorizationEntities.stream()
        .flatMap(a -> a.permissionTypes().stream())
        .collect(Collectors.toSet());
  }

  private <T> T getOrElseDefaultResult(
      final CamundaAuthentication authentication,
      final Function<Map<EntityType, Set<String>>, T> resultSupplier,
      final Supplier<T> defaultResultSupplier) {
    final var ownerTypeToOwnerIds = collectOwnerTypeToOwnerIds(authentication);
    return Optional.of(ownerTypeToOwnerIds)
        .filter(m -> !m.isEmpty())
        .map(resultSupplier)
        .orElseGet(defaultResultSupplier);
  }

  private Map<EntityType, Set<String>> collectOwnerTypeToOwnerIds(
      final CamundaAuthentication authentication) {
    final var ownerTypeToOwnerIds = new HashMap<EntityType, Set<String>>();
    if (authentication.authenticatedUsername() != null) {
      ownerTypeToOwnerIds.put(EntityType.USER, Set.of(authentication.authenticatedUsername()));
    }
    if (authentication.authenticatedClientId() != null) {
      ownerTypeToOwnerIds.put(EntityType.CLIENT, Set.of(authentication.authenticatedClientId()));
    }

    final var authenticatedGroupIds = authentication.authenticatedGroupIds();
    if (authenticatedGroupIds != null && !authenticatedGroupIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.GROUP, new HashSet<>(authenticatedGroupIds));
    }

    final var authenticatedRoleIds = authentication.authenticatedRoleIds();
    if (authenticatedRoleIds != null && !authenticatedRoleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.ROLE, new HashSet<>(authenticatedRoleIds));
    }

    final var authenticatedMappingRuleIds = authentication.authenticatedMappingRuleIds();
    if (authenticatedMappingRuleIds != null && !authenticatedMappingRuleIds.isEmpty()) {
      ownerTypeToOwnerIds.put(EntityType.MAPPING_RULE, new HashSet<>(authenticatedMappingRuleIds));
    }

    return ownerTypeToOwnerIds;
  }
}
