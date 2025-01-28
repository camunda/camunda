/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.security.entity.Permission;
import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;

public class AuthorizationEntity extends AbstractExporterEntity<AuthorizationEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  private String id;
  private String ownerId;
  private Long ownerKey;
  private String ownerType;
  private String resourceType;
  private String resourceId;
  private List<PermissionType> authorizationPermissions;
  private List<Permission> permissions;

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

  public String getOwnerId() {
    return ownerId;
  }

  public AuthorizationEntity setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
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

  public String getResourceId() {
    return resourceId;
  }

  public AuthorizationEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public List<PermissionType> getAuthorizationPermissions() {
    return authorizationPermissions;
  }

  public AuthorizationEntity setAuthorizationPermissions(
      final List<PermissionType> authorizationPermissions) {
    this.authorizationPermissions = authorizationPermissions;
    return this;
  }

  public List<Permission> getPermissions() {
    return permissions == null ? List.of() : permissions;
  }

  public AuthorizationEntity setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }
}
