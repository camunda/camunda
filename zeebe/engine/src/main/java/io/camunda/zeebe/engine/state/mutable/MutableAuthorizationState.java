/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;

public interface MutableAuthorizationState extends AuthorizationState {

  /**
   * Stores the provided authorization in the state.
   *
   * @param authorizationKey the key of the authorization
   * @param authorization the authorization record to store
   */
  void create(final long authorizationKey, final AuthorizationRecord authorization);

  /**
   * Removes all permissions for the provided ownerKey.
   *
   * @param ownerType the type of the owner of the authorizations
   * @param ownerId the ID of the owner of the authorizations
   */
  void deleteAuthorizationsByOwnerTypeAndIdPrefix(
      final AuthorizationOwnerType ownerType, final String ownerId);

  /**
   * Updates the provided authorization in the state.
   *
   * @param authorizationKey the key of the authorization
   * @param authorization the authorization record to update
   */
  void update(final long authorizationKey, final AuthorizationRecord authorization);

  /**
   * Removes the authorization with the provided key.
   *
   * @param authorizationKey the key of the authorization to remove
   */
  void delete(final long authorizationKey);
}
