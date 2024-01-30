/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import java.util.List;
import java.util.Objects;

public class UserEntity extends OperateEntity<UserEntity> {
  private String userId;
  private String displayName;
  private String password;
  private List<String> roles;

  public List<String> getRoles() {
    return roles;
  }

  public UserEntity setRoles(List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserEntity setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UserEntity setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final UserEntity that = (UserEntity) o;
    return userId.equals(that.userId) && displayName.equals(that.displayName) && password.equals(
        that.password) && roles.equals(that.roles);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, displayName, password, roles);
  }
}
