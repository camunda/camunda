/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.authorization.PersistedAuthorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Optional;
import java.util.Set;

public interface AuthorizationState {
  /** Returns the authorization visible to a command outside of a policy update. */
  default Optional<PersistedAuthorization> get(final long authorizationKey) {
    return get(authorizationKey, 0);
  }

  /**
   * Returns the authorization visible to a command of the given policy version. A command sees the
   * current version and its own version, so a policy update reads its own writes.
   */
  Optional<PersistedAuthorization> get(long authorizationKey, long commandVersion);

  default Set<AuthorizationScope> getAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return getAuthorizationScopes(ownerType, ownerId, resourceType, permissionType, 0);
  }

  Set<AuthorizationScope> getAuthorizationScopes(
      AuthorizationOwnerType ownerType,
      String ownerId,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      long commandVersion);

  default Set<Long> getAuthorizationKeysForOwner(
      final AuthorizationOwnerType ownerType, final String ownerId) {
    return getAuthorizationKeysForOwner(ownerType, ownerId, 0);
  }

  Set<Long> getAuthorizationKeysForOwner(
      AuthorizationOwnerType ownerType, String ownerId, long commandVersion);
}
