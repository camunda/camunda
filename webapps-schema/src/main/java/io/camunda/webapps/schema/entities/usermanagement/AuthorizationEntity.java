/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Set;

public class AuthorizationEntity extends AbstractExporterEntity<AuthorizationEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  @BeforeVersion880 private Long authorizationKey;
  @BeforeVersion880 private String ownerId;
  @BeforeVersion880 private String ownerType;
  @BeforeVersion880 private String resourceType;
  @BeforeVersion880 private Short resourceMatcher;
  @BeforeVersion880 private String resourceId;
  @BeforeVersion880 private Set<PermissionType> permissionTypes;

  // Default to empty string for backward compatibility. All new resourceId-based authorizations
  // (starting 8.9) will have an empty string value for resourcePropertyName. For authorizations
  // created before 8.9, this field will be null in the database, so we set this default value
  // to provide the same output as new resourceId-based authorizations.
  @SinceVersion(value = "8.9.0")
  private String resourcePropertyName = "";

  public AuthorizationEntity() {}

  public Long getAuthorizationKey() {
    return authorizationKey;
  }

  public AuthorizationEntity setAuthorizationKey(final Long authorizationKey) {
    this.authorizationKey = authorizationKey;
    return this;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public AuthorizationEntity setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
    return this;
  }

  public String getOwnerType() {
    return ownerType;
  }

  public AuthorizationEntity setOwnerType(final String ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  public Short getResourceMatcher() {
    return resourceMatcher;
  }

  public AuthorizationEntity setResourceMatcher(final Short resourceMatcher) {
    this.resourceMatcher = resourceMatcher;
    return this;
  }

  public String getResourceId() {
    return resourceId;
  }

  public AuthorizationEntity setResourceId(final String resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public String getResourcePropertyName() {
    return resourcePropertyName;
  }

  public AuthorizationEntity setResourcePropertyName(final String resourcePropertyName) {
    this.resourcePropertyName = resourcePropertyName;
    return this;
  }

  public String getResourceType() {
    return resourceType;
  }

  public AuthorizationEntity setResourceType(final String resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public Set<PermissionType> getPermissionTypes() {
    return permissionTypes == null ? Set.of() : permissionTypes;
  }

  public AuthorizationEntity setPermissionTypes(final Set<PermissionType> permissionTypes) {
    this.permissionTypes = permissionTypes;
    return this;
  }
}
