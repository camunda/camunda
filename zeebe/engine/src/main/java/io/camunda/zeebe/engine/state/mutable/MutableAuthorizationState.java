/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

public interface MutableAuthorizationState extends AuthorizationState {

  /**
   * Checks if a Permission exists for the provided ownerKey, resourceType and permissionType. If it
   * does, adds the resourceIds to this entry. If it does not, creates a new Permission with the
   * provided resourceIds.
   *
   * @param ownerKey the key of the owner of the permissions. This could be a userKey, a roleKey or
   *     a groupKey
   * @param resourceType the type of resource the permissions are for (Eg. Process definition, Job)
   * @param permissionType The type of permission being granted (Eg. READ, WRITE)
   * @param resourceIds A list of resourceIds the permissions are granted for (Eg. bpmnProcessId, *)
   */
  void createOrAddPermission(
      long ownerKey,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      List<String> resourceIds);

  /**
   * Removes the resource ids for the provided ownerKey, resourceType, permissionType. If there are
   * no other resourceIds left for this entry, the entire entry will be deleted.
   *
   * @param ownerKey the key of the owner of the permissions. This could be a userKey, a roleKey or
   *     a groupKey
   * @param resourceType the type of resource the permissions are for (Eg. Process definition, Job)
   * @param permissionType The type of permission being granted (Eg. READ, WRITE)
   * @param resourceIds A list of resourceIds the permissions are granted for (Eg. bpmnProcessId, *)
   */
  void removePermission(
      long ownerKey,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      List<String> resourceIds);

  /**
   * Stores the owner type for a new owner in the state.
   *
   * @param ownerKey the key of the owner
   * @param ownerType the type of the owner
   */
  void insertOwnerTypeByKey(final long ownerKey, final AuthorizationOwnerType ownerType);

  /**
   * Removes all permissions for the provided ownerKey.
   *
   * @param ownerKey the key of the owner of the authorizations
   */
  void deleteAuthorizationsByOwnerKeyPrefix(final long ownerKey);

  /**
   * Removes the owner type for the provided ownerKey.
   *
   * @param ownerKey the key of the owner
   */
  void deleteOwnerTypeByKey(final long ownerKey);
}
