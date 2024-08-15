/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import java.util.List;

public class AuthorizationDto {

  protected String id;
  protected Integer type;
  protected List<String> permissions;
  protected String userId;
  protected String groupId;
  protected Integer resourceType;
  protected String resourceId;

  public AuthorizationDto(
      final String id,
      final Integer type,
      final List<String> permissions,
      final String userId,
      final String groupId,
      final Integer resourceType,
      final String resourceId) {
    this.id = id;
    this.type = type;
    this.permissions = permissions;
    this.userId = userId;
    this.groupId = groupId;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
  }

  public AuthorizationDto() {}

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Integer getType() {
    return type;
  }

  public void setType(final Integer type) {
    this.type = type;
  }

  public List<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(final List<String> permissions) {
    this.permissions = permissions;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getGroupId() {
    return groupId;
  }

  public void setGroupId(final String groupId) {
    this.groupId = groupId;
  }

  public Integer getResourceType() {
    return resourceType;
  }

  public void setResourceType(final Integer resourceType) {
    this.resourceType = resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(final String resourceId) {
    this.resourceId = resourceId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizationDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $permissions = getPermissions();
    result = result * PRIME + ($permissions == null ? 43 : $permissions.hashCode());
    final Object $userId = getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    final Object $groupId = getGroupId();
    result = result * PRIME + ($groupId == null ? 43 : $groupId.hashCode());
    final Object $resourceType = getResourceType();
    result = result * PRIME + ($resourceType == null ? 43 : $resourceType.hashCode());
    final Object $resourceId = getResourceId();
    result = result * PRIME + ($resourceId == null ? 43 : $resourceId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizationDto)) {
      return false;
    }
    final AuthorizationDto other = (AuthorizationDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$permissions = getPermissions();
    final Object other$permissions = other.getPermissions();
    if (this$permissions == null
        ? other$permissions != null
        : !this$permissions.equals(other$permissions)) {
      return false;
    }
    final Object this$userId = getUserId();
    final Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) {
      return false;
    }
    final Object this$groupId = getGroupId();
    final Object other$groupId = other.getGroupId();
    if (this$groupId == null ? other$groupId != null : !this$groupId.equals(other$groupId)) {
      return false;
    }
    final Object this$resourceType = getResourceType();
    final Object other$resourceType = other.getResourceType();
    if (this$resourceType == null
        ? other$resourceType != null
        : !this$resourceType.equals(other$resourceType)) {
      return false;
    }
    final Object this$resourceId = getResourceId();
    final Object other$resourceId = other.getResourceId();
    if (this$resourceId == null
        ? other$resourceId != null
        : !this$resourceId.equals(other$resourceId)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizationDto(id="
        + getId()
        + ", type="
        + getType()
        + ", permissions="
        + getPermissions()
        + ", userId="
        + getUserId()
        + ", groupId="
        + getGroupId()
        + ", resourceType="
        + getResourceType()
        + ", resourceId="
        + getResourceId()
        + ")";
  }
}
