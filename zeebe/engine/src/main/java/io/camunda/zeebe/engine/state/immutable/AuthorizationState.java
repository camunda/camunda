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
  Optional<PersistedAuthorization> get(final long authorizationKey);

  Set<AuthorizationScope> getAuthorizationScopes(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      AuthorizationResourceType resourceType,
      final PermissionType permissionType);

  Set<Long> getAuthorizationKeysForOwner(
      final AuthorizationOwnerType ownerType, final String ownerId);
}
