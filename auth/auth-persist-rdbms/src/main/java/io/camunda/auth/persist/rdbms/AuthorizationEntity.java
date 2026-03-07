/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

/** Entity mapping for the AUTH_AUTHORIZATION table. */
public class AuthorizationEntity {

  private long authorizationKey;
  private String ownerId;
  private String ownerType;
  private String resourceType;
  private String resourceId;
  private String permissionTypes;

  public long getAuthorizationKey() {
    return authorizationKey;
  }

  public void setAuthorizationKey(final long authorizationKey) {
    this.authorizationKey = authorizationKey;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(final String ownerId) {
    this.ownerId = ownerId;
  }

  public String getOwnerType() {
    return ownerType;
  }

  public void setOwnerType(final String ownerType) {
    this.ownerType = ownerType;
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

  public String getPermissionTypes() {
    return permissionTypes;
  }

  public void setPermissionTypes(final String permissionTypes) {
    this.permissionTypes = permissionTypes;
  }
}
