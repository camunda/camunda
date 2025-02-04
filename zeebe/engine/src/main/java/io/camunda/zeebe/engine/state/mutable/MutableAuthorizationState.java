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
   * Checks if a Permission exists for the provided ownerKey, resourceType and permissionType. If it
   * does, adds the resourceIds to this entry. If it does not, creates a new Permission with the
   * provided resourceIds.
   *
   * @param ownerType the type of the owner of the permissions. This could be a user, a role, or a
   *     group
   * @param ownerId the ID of the owner of the permissions. This could be a username, a roleId or a
   *     groupId
   * @param resourceType the type of resource the permissions are for (Eg. Process definition, Job)
   * @param permissionType The type of permission being granted (Eg. READ, WRITE)
   * @param resourceIds A set of resourceIds the permissions are granted for (Eg. bpmnProcessId, *)
   */
  void createOrAddPermission(
      AuthorizationOwnerType ownerType,
      String ownerId,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      Set<String> resourceIds);

  /**
   * Stores the provided authorization in the state.
   *
   * @param authorizationKey the key of the authorization
   * @param authorization the authorization record to store
   */
  void create(final long authorizationKey, final AuthorizationRecord authorization);

  /**
   * Removes the resource ids for the provided ownerKey, resourceType, permissionType. If there are
   * no other resourceIds left for this entry, the entire entry will be deleted.
   *
   * @param ownerId the ID of the owner of the permissions. This could be a username, a roleId or a
   *     groupId
   * @param resourceType the type of resource the permissions are for (Eg. Process definition, Job)
   * @param permissionType The type of permission being granted (Eg. READ, WRITE)
   * @param resourceIds A set of resourceIds the permissions are granted for (Eg. bpmnProcessId, *)
   */
  void removePermission(
      AuthorizationOwnerType ownerType,
      String ownerId,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      Set<String> resourceIds);

  /**
   * Removes all permissions for the provided ownerKey.
   *
   * @param ownerType the type of the owner of the authorizations
   * @param ownerId the ID of the owner of the authorizations
   */
  void deleteAuthorizationsByOwnerTypeAndIdPrefix(
      final AuthorizationOwnerType ownerType, final String ownerId);

  /**
   * Removes the authorization with the provided key.
   *
   * @param authorizationKey the key of the authorization to remove
   */
  void delete(final long authorizationKey);
}
