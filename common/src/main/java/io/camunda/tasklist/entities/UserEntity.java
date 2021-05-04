/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.entities;

public class UserEntity extends TasklistEntity<UserEntity> {

  private String username;
  private String password;
  private String role;
  private String firstname;
  private String lastname;

  public String getRole() {
    return role;
  }

  public UserEntity setRole(String role) {
    this.role = role;
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
    result = 31 * result + (role != null ? role.hashCode() : 0);
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

    if (username != null ? !username.equals(that.username) : that.username != null) {
      return false;
    }
    if (password != null ? !password.equals(that.password) : that.password != null) {
      return false;
    }
    if (role != null ? !role.equals(that.role) : that.role != null) {
      return false;
    }
    if (firstname != null ? !firstname.equals(that.firstname) : that.firstname != null) {
      return false;
    }
    return lastname != null ? lastname.equals(that.lastname) : that.lastname == null;
  }

  public static UserEntity from(String username, String password, String role) {
    final UserEntity userEntity = new UserEntity();
    userEntity.setId(username);
    userEntity.setUsername(username);
    userEntity.setPassword(password);
    userEntity.setRole(role);
    return userEntity;
  }
}
