/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceIdFormat;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationEntity(
    Long authorizationKey,
    String ownerId,
    String ownerType,
    String resourceType,
    ResourceIdFormat resourceIdFormat,
    String resourceId,
    Set<PermissionType> permissionTypes) {

  public AuthorizationEntity(
      final Long authorizationKey,
      final String ownerId,
      final String ownerType,
      final String resourceType,
      final String resourceId,
      final Set<PermissionType> permissionTypes) {
    this(
        authorizationKey,
        ownerId,
        ownerType,
        resourceType,
        ResourceIdFormat.ID,
        resourceId,
        permissionTypes);
  }
}
