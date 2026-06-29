/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.adapter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.AuthorizationScope;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort;
import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class AuthorizationScopeStateAdapter implements AuthorizationScopeRepositoryPort {

  private final AuthorizationState authorizationState;
  private final LoadingCache<
          ScopeCacheKey, Set<io.camunda.zeebe.protocol.record.value.AuthorizationScope>>
      scopeCache;

  public AuthorizationScopeStateAdapter(final AuthorizationState authorizationState) {
    this.authorizationState = authorizationState;
    scopeCache =
        CacheBuilder.newBuilder()
            .maximumSize(1_000)
            .build(
                new CacheLoader<>() {
                  @Override
                  public Set<io.camunda.zeebe.protocol.record.value.AuthorizationScope> load(
                      final ScopeCacheKey key) {
                    return AuthorizationScopeStateAdapter.this.authorizationState
                        .getAuthorizationScopes(
                            key.ownerType(),
                            key.ownerId(),
                            key.resourceType(),
                            key.permissionType());
                  }
                });
  }

  private record ScopeCacheKey(
      AuthorizationOwnerType ownerType,
      String ownerId,
      io.camunda.zeebe.protocol.record.value.AuthorizationResourceType resourceType,
      io.camunda.zeebe.protocol.record.value.PermissionType permissionType) {}

  @Override
  public List<AuthorizationScope> findAuthorizedScopes(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    final var zeebeResourceType = AuthzModelMapper.toProtocol(resourceType);
    final var zeebePermissionType = AuthzModelMapper.toProtocol(permissionType);
    final var result = new HashSet<AuthorizationScope>();
    for (final var entry : ownerIds.entrySet()) {
      final var ownerType = toAuthorizationOwnerType(entry.getKey());
      for (final var ownerId : entry.getValue()) {
        getAuthorizationScopes(ownerType, ownerId, zeebeResourceType, zeebePermissionType).stream()
            .map(AuthzModelMapper::fromProtocol)
            .forEach(result::add);
      }
    }
    return new ArrayList<>(result);
  }

  @Override
  public boolean hasAuthorizedScope(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final List<String> resourceIds) {
    final var zeebeResourceType = AuthzModelMapper.toProtocol(resourceType);
    final var zeebePermissionType = AuthzModelMapper.toProtocol(permissionType);
    for (final var entry : ownerIds.entrySet()) {
      final var ownerType = toAuthorizationOwnerType(entry.getKey());
      for (final var ownerId : entry.getValue()) {
        for (final var scope :
            getAuthorizationScopes(ownerType, ownerId, zeebeResourceType, zeebePermissionType)) {
          if (matchesCslScopeByResourceId(AuthzModelMapper.fromProtocol(scope), resourceIds)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public Set<PermissionType> findPermissionTypes(
      final Map<EntityType, Set<String>> ownerIds,
      final AuthorizationResourceType resourceType,
      final List<String> resourceIds) {
    final var zeebeResourceType = AuthzModelMapper.toProtocol(resourceType);
    final var result = new HashSet<PermissionType>();
    for (final var entry : ownerIds.entrySet()) {
      final var ownerType = toAuthorizationOwnerType(entry.getKey());
      for (final var ownerId : entry.getValue()) {
        authorizationState.getAuthorizationKeysForOwner(ownerType, ownerId).stream()
            .map(authorizationState::get)
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get())
            .filter(auth -> auth.getResourceType() == zeebeResourceType)
            .filter(auth -> matchesPersistedAuthByResourceId(auth, resourceIds))
            .flatMap(auth -> auth.getPermissionTypes().stream())
            .map(AuthzModelMapper::fromProtocol)
            .forEach(result::add);
      }
    }
    return result;
  }

  private Set<io.camunda.zeebe.protocol.record.value.AuthorizationScope> getAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final io.camunda.zeebe.protocol.record.value.AuthorizationResourceType resourceType,
      final io.camunda.zeebe.protocol.record.value.PermissionType permissionType) {
    return scopeCache.getUnchecked(
        new ScopeCacheKey(ownerType, ownerId, resourceType, permissionType));
  }

  private AuthorizationOwnerType toAuthorizationOwnerType(final EntityType cslEntityType) {
    return switch (cslEntityType) {
      case USER -> AuthorizationOwnerType.USER;
      case CLIENT -> AuthorizationOwnerType.CLIENT;
      case GROUP -> AuthorizationOwnerType.GROUP;
      case ROLE -> AuthorizationOwnerType.ROLE;
      case MAPPING_RULE -> AuthorizationOwnerType.MAPPING_RULE;
      case UNSPECIFIED -> AuthorizationOwnerType.UNSPECIFIED;
    };
  }

  private boolean matchesCslScopeByResourceId(
      final AuthorizationScope scope, final List<String> resourceIds) {
    return switch (scope.getMatcher()) {
      case ANY -> true;
      case ID -> resourceIds.contains(scope.getResourceId());
      case PROPERTY, UNSPECIFIED -> false;
    };
  }

  private boolean matchesPersistedAuthByResourceId(
      final PersistedAuthorization auth, final List<String> resourceIds) {
    return switch (auth.getResourceMatcher()) {
      case ANY -> true;
      case ID -> resourceIds.contains(auth.getResourceId());
      case PROPERTY, UNSPECIFIED -> false;
    };
  }
}
