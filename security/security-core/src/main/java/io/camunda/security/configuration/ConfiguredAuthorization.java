/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;

public record ConfiguredAuthorization(
    AuthorizationOwnerType ownerType,
    String ownerId,
    AuthorizationResourceType resourceType,
    String resourceId,
    String resourcePropertyName,
    Set<PermissionType> permissions) {

  public static ConfiguredAuthorization idBased(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final Set<PermissionType> permissions) {
    return new ConfiguredAuthorization(
        ownerType, ownerId, resourceType, resourceId, null, permissions);
  }

  public static ConfiguredAuthorization wildcard(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final Set<PermissionType> permissions) {
    return idBased(ownerType, ownerId, resourceType, AuthorizationScope.WILDCARD_CHAR, permissions);
  }

  public static ConfiguredAuthorization propertyBased(
      final AuthorizationOwnerType ownerType,
      final String ownerId,
      final AuthorizationResourceType resourceType,
      final String resourcePropertyName,
      final Set<PermissionType> permissions) {
    return new ConfiguredAuthorization(
        ownerType, ownerId, resourceType, null, resourcePropertyName, permissions);
  }

  public boolean isIdBased() {
    return resourceId != null && !resourceId.isBlank();
  }

  public boolean isPropertyBased() {
    return resourcePropertyName != null && !resourcePropertyName.isBlank();
  }
}
