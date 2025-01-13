/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;

public class AuthorizationEntity extends AbstractExporterEntity<AuthorizationEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  // Composite ID: ownerKey + resourceType + permissionType + resourceId
  private String id;
  private Long ownerKey;
  private String ownerType;
  private String resourceType;
  private PermissionType permissionType;
  private String resourceId;

  public AuthorizationEntity() {}

  @Override
  public String getId() {
    return id;
  }

  @Override
  public AuthorizationEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public Long getOwnerKey() {
    return ownerKey;
  }

  public AuthorizationEntity setOwnerKey(final Long ownerKey) {
    this.ownerKey = ownerKey;
    return this;
  }

  public String getOwnerType() {
    return ownerType;
  }

  public AuthorizationEntity setOwnerType(final String ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  public String getResourceType() {
    return resourceType;
  }

  public AuthorizationEntity setResourceType(final String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public PermissionType getPermissionType() {
    return permissionType;
  }

  public AuthorizationEntity setPermissionType(final PermissionType permissionType) {
    this.permissionType = permissionType;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public AuthorizationEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }
}
