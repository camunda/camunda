/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.entities;

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;

public class AuthorizationEntity implements ExporterEntity<AuthorizationEntity> {
  private String id;
  private Long ownerKey;
  private AuthorizationOwnerType ownerType;
  private AuthorizationResourceType resourceType;
  private Map<PermissionType, List<String>> permissionValues;

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

  public AuthorizationOwnerType getOwnerType() {
    return ownerType;
  }

  public AuthorizationEntity setOwnerType(final AuthorizationOwnerType ownerType) {
    this.ownerType = ownerType;
    return this;
  }

  public AuthorizationResourceType getResourceType() {
    return resourceType;
  }

  public AuthorizationEntity setResourceType(final AuthorizationResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public Map<PermissionType, List<String>> getPermissionValues() {
    return permissionValues == null ? Map.of() : permissionValues;
  }

  public AuthorizationEntity setPermissionValues(
      final Map<PermissionType, List<String>> permissionValues) {
    this.permissionValues = permissionValues;
    return this;
  }
}
