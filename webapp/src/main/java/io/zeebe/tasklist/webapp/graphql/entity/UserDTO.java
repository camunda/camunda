/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.graphql.entity;

import static io.zeebe.tasklist.util.CollectionUtil.map;

import io.zeebe.tasklist.entities.UserEntity;
import java.util.List;

public class UserDTO {

  private String username;
  private String firstname;
  private String lastname;

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

  public static UserDTO createFrom(UserEntity userEntity) {
    return new UserDTO()
        .setUsername(userEntity.getUsername())
        .setFirstname(userEntity.getFirstname())
        .setLastname(userEntity.getLastname());
  }

  public static List<UserDTO> createFrom(List<UserEntity> userEntities) {
    return map(userEntities, t -> createFrom(t));
  }

  @Override
  public int hashCode() {
    int result = username != null ? username.hashCode() : 0;
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

    final UserDTO user = (UserDTO) o;

    if (username != null ? !username.equals(user.username) : user.username != null) {
      return false;
    }
    if (firstname != null ? !firstname.equals(user.firstname) : user.firstname != null) {
      return false;
    }
    return lastname != null ? lastname.equals(user.lastname) : user.lastname == null;
  }
}
