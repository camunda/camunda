/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import java.util.List;
import java.util.Objects;

public class UserEntity extends AbstractExporterEntity<UserEntity> {
  private String userId;
  private String displayName;
  private String password;
  private List<String> roles;

  public List<String> getRoles() {
    return roles;
  }

  public UserEntity setRoles(final List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(final String password) {
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
  public int hashCode() {
    return Objects.hash(super.hashCode(), userId, displayName, password, roles);
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
    return userId.equals(that.userId)
        && displayName.equals(that.displayName)
        && password.equals(that.password)
        && roles.equals(that.roles);
  }
}
