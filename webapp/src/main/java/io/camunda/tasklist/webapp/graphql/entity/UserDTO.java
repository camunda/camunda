/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.webapp.security.Permission;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class UserDTO {

  private String userId;
  private String displayName;
  private boolean apiUser;

  private List<Permission> permissions;
  private List<String> roles;

  private String salesPlanType;

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

  public boolean isApiUser() {
    return apiUser;
  }

  public UserDTO setApiUser(boolean apiUser) {
    this.apiUser = apiUser;
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

  public UserDTO setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, displayName, apiUser, permissions, roles, salesPlanType);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final UserDTO other = (UserDTO) obj;
    return Objects.equals(apiUser, other.apiUser)
        && Objects.equals(displayName, other.displayName)
        && Objects.equals(userId, other.userId)
        && Objects.equals(permissions, other.permissions)
        && Objects.equals(roles, other.roles)
        && Objects.equals(salesPlanType, other.salesPlanType);
  }
}
