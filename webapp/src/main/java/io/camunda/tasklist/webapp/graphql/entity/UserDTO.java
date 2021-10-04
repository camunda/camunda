/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.webapp.security.Permission;
import java.util.List;
import java.util.Objects;

public class UserDTO {

  private String username;
  private String firstname;
  private String lastname;
  private boolean apiUser;

  private List<Permission> permissions;

  public String getUsername() {
    return username;
  }

  public UserDTO setUsername(String username) {
    this.username = username;
    return this;
  }

  public String getFirstname() {
    return firstname;
  }

  public UserDTO setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public String getLastname() {
    return lastname;
  }

  public UserDTO setLastname(String lastname) {
    this.lastname = lastname;
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

  @Override
  public int hashCode() {
    return Objects.hash(firstname, lastname, username, apiUser, permissions);
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
    return Objects.equals(firstname, other.firstname)
        && Objects.equals(apiUser, other.apiUser)
        && Objects.equals(lastname, other.lastname)
        && Objects.equals(username, other.username)
        && Objects.equals(permissions, other.permissions);
  }
}
