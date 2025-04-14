/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.tenant.TasklistTenant;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class UserDTO {

  private String userId;
  private String displayName;
  private List<Permission> permissions;
  private List<String> roles;
  private String salesPlanType;
  private List<C8AppLink> c8Links = List.of();
  private List<TasklistTenant> tenants = List.of();
  private List<String> groups = List.of();

  public String getUserId() {
    return userId;
  }

  public UserDTO setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    if (!StringUtils.hasText(displayName)) {
      return userId;
    }
    return displayName;
  }

  public UserDTO setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  public List<Permission> getPermissions() {
    return permissions;
  }

  public UserDTO setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }

  public List<String> getRoles() {
    return roles;
  }

  public UserDTO setRoles(final List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  public UserDTO setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public List<C8AppLink> getC8Links() {
    return c8Links;
  }

  public UserDTO setC8Links(final List<C8AppLink> c8Links) {
    if (c8Links != null) {
      this.c8Links = c8Links;
    }
    return this;
  }

  public List<TasklistTenant> getTenants() {
    return tenants;
  }

  public UserDTO setTenants(final List<TasklistTenant> tenants) {
    if (tenants != null) {
      this.tenants = tenants;
    }
    return this;
  }

  public List<String> getGroups() {
    return groups;
  }

  public UserDTO setGroups(final List<String> groups) {
    this.groups = groups;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userId, displayName, permissions, roles, salesPlanType, c8Links, tenants, groups);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserDTO userDTO = (UserDTO) o;
    return Objects.equals(userId, userDTO.userId)
        && Objects.equals(displayName, userDTO.displayName)
        && Objects.equals(permissions, userDTO.permissions)
        && Objects.equals(roles, userDTO.roles)
        && Objects.equals(salesPlanType, userDTO.salesPlanType)
        && Objects.equals(c8Links, userDTO.c8Links)
        && Objects.equals(tenants, userDTO.tenants)
        && Objects.equals(groups, userDTO.groups);
  }

  @Override
  public String toString() {
    return "UserDTO{"
        + "userId='"
        + userId
        + '\''
        + ", displayName='"
        + displayName
        + '\''
        + ", permissions="
        + permissions
        + ", roles="
        + roles
        + ", salesPlanType='"
        + salesPlanType
        + '\''
        + ", c8Links="
        + c8Links
        + ", tenants="
        + tenants
        + ", groups="
        + groups
        + '}';
  }
}
