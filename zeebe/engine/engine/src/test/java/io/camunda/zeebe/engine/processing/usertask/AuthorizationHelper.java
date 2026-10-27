/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;

/**
 * Test utility for configuring authorization permissions in user task authorization tests.
 *
 * <p>Provides a convenient wrapper around {@link EngineRule#authorization()} to assign permissions
 * for specific users, resources, and scopes used in user task scenarios.
 */
public final class AuthorizationHelper {

  private final EngineRule engine;

  public AuthorizationHelper(final EngineRule engine) {
    this.engine = engine;
  }

  public void addPermissionsToUser(
      final String assignToPermissionUsername,
      final String adminUsername,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final AuthorizationScope authorizationScope) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(assignToPermissionUsername)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(authorization)
        .withResourceMatcher(authorizationScope.getMatcher())
        .withResourceId(authorizationScope.getResourceId())
        .withResourcePropertyName(authorizationScope.getResourcePropertyName())
        .create(adminUsername);
  }
}
