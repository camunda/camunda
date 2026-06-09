/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.entities.AuthorizationEntity;
import io.camunda.search.query.AuthorizationQuery;
import io.camunda.security.api.model.authz.AuthorizationResourceMatcher;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.authz.ResourceAccessChecks;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Search-backed implementation of {@link AuthorizationScopeRepositoryPort} that delegates all
 * authorization scope queries to the {@link AuthorizationReader} search infrastructure.
 *
 * <p>Each method issues an {@link AuthorizationQuery} without resource-access checks ({@link
 * ResourceAccessChecks#disabled()}), because this class <em>is</em> the authorization data source —
 * applying access control recursively here would cause infinite recursion.
 *
 * <p>Callers are responsible for short-circuiting when {@code ownerIds} is empty (e.g. anonymous
 * principals); CSL's {@link io.camunda.security.core.authz.AuthorizationChecker} performs this
 * guard before invoking any port method.
 */
@NullMarked
public class SearchAuthorizationScopeRepository implements AuthorizationScopeRepositoryPort {

  private final AuthorizationReader authorizationReader;

  public SearchAuthorizationScopeRepository(final AuthorizationReader authorizationReader) {
    this.authorizationReader = authorizationReader;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Results are post-filtered by {@code permissionType} to exclude records that matched the
   * index filter on resource type but do not carry the requested permission.
   */
  @Override
  public List<AuthorizationScope> findAuthorizedScopes(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
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
        .map(this::toAuthorizationScope)
        .toList();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Issues a page-size-1 query so the search backend can short-circuit after finding the first
   * matching record.
   */
  @Override
  public boolean hasAuthorizedScope(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final List<String> resourceIds) {
    final var query =
        AuthorizationQuery.of(
            q ->
                q.filter(
                        f ->
                            f.ownerTypeToOwnerIds(ownerIds)
                                .resourceType(resourceType.name())
                                .permissionTypes(permissionType)
                                .resourceIds(resourceIds))
                    .page(p -> p.size(1)));
    return authorizationReader.search(query, ResourceAccessChecks.disabled()).total() > 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Collects the union of {@link PermissionType} values across all matching authorization
   * records.
   */
  @Override
  public Set<PermissionType> findPermissionTypes(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final List<String> resourceIds) {
    final var query =
        AuthorizationQuery.of(
            q ->
                q.filter(
                        f ->
                            f.ownerTypeToOwnerIds(ownerIds)
                                .resourceType(resourceType.name())
                                .resourceIds(resourceIds))
                    .unlimited());
    return authorizationReader.search(query, ResourceAccessChecks.disabled()).items().stream()
        .flatMap(e -> e.permissionTypes().stream())
        .collect(Collectors.toSet());
  }

  private AuthorizationScope toAuthorizationScope(final AuthorizationEntity entity) {
    return new AuthorizationScope(
        entity.resourceMatcher() != null
            ? AuthorizationResourceMatcher.from(entity.resourceMatcher())
            : null,
        entity.resourceId(),
        entity.resourcePropertyName());
  }
}
