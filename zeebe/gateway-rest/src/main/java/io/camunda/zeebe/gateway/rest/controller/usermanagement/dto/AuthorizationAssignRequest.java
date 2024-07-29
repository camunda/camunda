/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement.dto;

import io.camunda.zeebe.protocol.impl.record.value.identity.AuthorizationOwnerType;
import java.util.List;

public class AuthorizationAssignRequest {
  private String ownerKey;
  private AuthorizationOwnerType ownerType;
  private String resourceKey;
  private String resourceType;
  private List<String> permissions;

  public String getOwnerKey() {
    return ownerKey;
  }

  public void setOwnerKey(final String ownerKey) {
    this.ownerKey = ownerKey;
  }

  public AuthorizationOwnerType getOwnerType() {
    return ownerType;
  }

  public void setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
  }

  public String getResourceKey() {
    return resourceKey;
  }

  public void setResourceKey(final String resourceKey) {
    this.resourceKey = resourceKey;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(final String resourceType) {
    this.resourceType = resourceType;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(final List<String> permissions) {
    this.permissions = permissions;
  }
}
