/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;

public class ConfiguredAuthorization {
  private String ownerId;
  private String resourceType;
  private String resourceId;
  private Set<PermissionType> permissions;

  public ConfiguredAuthorization(
      final String ownerId,
      final String resourceType,
      final String resourceId,
      final Set<PermissionType> permissions) {
    this.ownerId = ownerId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.permissions = permissions;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(final String resourceType) {
    this.resourceType = resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  public Set<PermissionType> getPermissions() {
    return permissions;
  }

  public void setPermissions(final Set<PermissionType> permissions) {
    this.permissions = permissions;
  }
}
