/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.security.entity.Permission;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthorizationEntity(
    String id,
    String ownerKey,
    String ownerType,
    String resourceType,
    String resourceId,
    List<PermissionType> permissions) {
  public AuthorizationEntity(
      final Long id,
      final Long ownerKey,
      final String ownerType,
      final String resourceType,
      final List<Permission> permissions) {
    this(
        Long.toString(id),
        Long.toString(ownerKey),
        ownerType,
        resourceType,
        null,
        permissions.stream().map(Permission::type).toList());
  }
}
