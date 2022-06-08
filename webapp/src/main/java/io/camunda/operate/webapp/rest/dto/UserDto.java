/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;

import java.util.Objects;
import org.springframework.util.StringUtils;

public class UserDto {

  private String userId;

  private String displayName;

  private boolean canLogout;

  private List<Permission> permissions;

  private List<String> roles;

  private String salesPlanType;


  public boolean isCanLogout() {
    return canLogout;
  }

  public UserDto setCanLogout(boolean canLogout) {
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
    if (!StringUtils.hasText(displayName)){
      return userId;
    }
    return displayName;
  }

  public UserDto setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  //TODO: Remove when frontend has removed usage of username
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

  public UserDto setSalesPlanType(final String salesPlanType) {
    this.salesPlanType = salesPlanType;
    return this;
  }

  public String getSalesPlanType() {
    return salesPlanType;
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
    return canLogout == userDto.canLogout && userId.equals(userDto.userId) && displayName.equals(
        userDto.displayName) && permissions.equals(userDto.permissions) &&
        Objects.equals(roles, userDto.roles) &&
        Objects.equals(salesPlanType, userDto.salesPlanType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, displayName, canLogout, permissions, roles, salesPlanType);
  }
}
