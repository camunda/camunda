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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

public interface MutableAuthorizationState extends AuthorizationState {

  void createAuthorization(final AuthorizationRecord authorizationRecord);

  /**
   * Checks if an Authorization exists for the provided ownerKey, resourceType and permissionType.
   * If it does, adds the permissions to this entry. If it does not, creates a new entry with the
   * provided permissions.
   *
   * @param ownerKey the key of the owner of the permissions. This could be a userKey, a roleKey or
   *     a groupKey
   * @param resourceType the type of resource the permissions are for (Eg. Process definition, Job)
   * @param permissionType The type of permission being granted (Eg. READ, WRITE)
   * @param resourceIds A list of resourceIds the permissions are granted for (Eg.
   *     bpmnProcessId:foo, *)
   */
  void createOrAddPermissions(
      long ownerKey,
      AuthorizationResourceType resourceType,
      PermissionType permissionType,
      List<String> resourceIds);
}
