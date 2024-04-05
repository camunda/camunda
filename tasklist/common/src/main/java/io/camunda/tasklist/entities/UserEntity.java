/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.entities;

import java.util.List;
import java.util.Objects;

public class UserEntity extends TasklistEntity<UserEntity> {

  private String userId;
  private String password;
  private String displayName;
  private List<String> roles;

  public List<String> getRoles() {
    return roles;
  }

  public UserEntity setRoles(List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserEntity setUserId(String userId) {
    this.userId = userId;
    setId(userId);
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getDisplayName() {
    return displayName;
  }

  public UserEntity setDisplayName(String displayName) {
    this.displayName = displayName;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, displayName, password, roles);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (getClass() != o.getClass()) {
      return false;
    }
    final UserEntity that = (UserEntity) o;

    return Objects.equals(userId, that.userId)
        && Objects.equals(displayName, that.displayName)
        && Objects.equals(password, that.password)
        && Objects.equals(roles, that.roles);
  }

  public static UserEntity from(String userId, String password, List<String> roles) {
    final UserEntity userEntity = new UserEntity();
    userEntity.setUserId(userId);
    userEntity.setPassword(password);
    userEntity.setRoles(roles);
    return userEntity;
  }
}
