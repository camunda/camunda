/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import static io.camunda.tasklist.util.CollectionUtil.map;

import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.webapp.security.es.User;
import java.util.List;
import java.util.Objects;

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

  // Used by web application context
  public static UserDTO createFrom(User userDetails) {
    return new UserDTO()
        .setUsername(userDetails.getUsername())
        .setFirstname(userDetails.getFirstname())
        .setLastname(userDetails.getLastname());
  }

  public static List<UserDTO> createFrom(List<UserEntity> userEntities) {
    return map(userEntities, UserDTO::createFrom);
  }

  private static UserDTO createFrom(UserEntity userEntity) {
    return new UserDTO()
        .setUsername(userEntity.getUsername())
        .setFirstname(userEntity.getFirstname())
        .setLastname(userEntity.getLastname());
  }

  @Override
  public int hashCode() {
    return Objects.hash(firstname, lastname, username);
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
        && Objects.equals(lastname, other.lastname)
        && Objects.equals(username, other.username);
  }
}
