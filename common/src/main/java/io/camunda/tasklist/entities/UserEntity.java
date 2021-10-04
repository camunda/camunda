/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.entities;

import java.util.List;
import java.util.Objects;

public class UserEntity extends TasklistEntity<UserEntity> {

  private String username;
  private String password;
  private List<String> roles;
  private String firstname;
  private String lastname;

  public List<String> getRoles() {
    return roles;
  }

  public UserEntity setRoles(List<String> roles) {
    this.roles = roles;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public UserEntity setUsername(String username) {
    this.username = username;
    setId(username);
    return this;
  }

  public String getPassword() {
    return password;
  }

  public UserEntity setPassword(String password) {
    this.password = password;
    return this;
  }

  public String getFirstname() {
    return firstname;
  }

  public UserEntity setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public String getLastname() {
    return lastname;
  }

  public UserEntity setLastname(String lastname) {
    this.lastname = lastname;
    return this;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (username != null ? username.hashCode() : 0);
    result = 31 * result + (password != null ? password.hashCode() : 0);
    result = 31 * result + (roles != null ? roles.hashCode() : 0);
    result = 31 * result + (firstname != null ? firstname.hashCode() : 0);
    result = 31 * result + (lastname != null ? lastname.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
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

    if (!Objects.equals(username, that.username)) {
      return false;
    }
    if (!Objects.equals(password, that.password)) {
      return false;
    }
    if (!Objects.equals(roles, that.roles)) {
      return false;
    }
    if (!Objects.equals(firstname, that.firstname)) {
      return false;
    }
    return Objects.equals(lastname, that.lastname);
  }

  public static UserEntity from(String username, String password, List<String> roles) {
    final UserEntity userEntity = new UserEntity();
    userEntity.setId(username);
    userEntity.setUsername(username);
    userEntity.setPassword(password);
    userEntity.setRoles(roles);
    return userEntity;
  }
}
