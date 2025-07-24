/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.model.ClusterMetadata;
import java.util.*;
import org.springframework.util.StringUtils;

public class UserDto {

  private String userId;

  private String displayName;

  private boolean canLogout;

  private List<Permission> permissions;

  private List<String> roles;

  private String salesPlanType;

  private Map<ClusterMetadata.AppName, String> c8Links = new HashMap<>();

  public boolean isCanLogout() {
    return canLogout;
  }

  public UserDto setCanLogout(final boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserDto setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    if (!StringUtils.hasText(displayName)) {
      return userId;
    }
    return displayName;
  }

  public UserDto setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  // TODO: Remove when frontend has removed usage of username
  public String getUsername() {
    return getDisplayName();
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public UserDto setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public UserDto setRoles(final List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public UserDto setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public Map<ClusterMetadata.AppName, String> getC8Links() {
    return c8Links;
  }

  public UserDto setC8Links(final Map<ClusterMetadata.AppName, String> c8Links) {
    if (c8Links != null) {
      this.c8Links = c8Links;
    }
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, displayName, canLogout, permissions, roles, salesPlanType, c8Links);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserDto userDto = (UserDto) o;
    return canLogout == userDto.canLogout
        && userId.equals(userDto.userId)
        && displayName.equals(userDto.displayName)
        && permissions.equals(userDto.permissions)
        && Objects.equals(roles, userDto.roles)
        && Objects.equals(salesPlanType, userDto.salesPlanType)
        && Objects.equals(c8Links, userDto.c8Links);
  }
}
