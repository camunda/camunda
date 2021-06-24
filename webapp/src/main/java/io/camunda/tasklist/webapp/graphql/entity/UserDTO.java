/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static io.camunda.tasklist.webapp.security.UserReader.DEFAULT_USER;
import static io.camunda.tasklist.webapp.security.UserReader.EMPTY;

import com.auth0.jwt.interfaces.Claim;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.webapp.security.es.User;
import io.camunda.tasklist.webapp.security.sso.TokenAuthentication;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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

  public static List<UserDTO> createFrom(List<UserEntity> userEntities) {
    return map(userEntities, UserDTO::createFrom);
  }

  // Used by web application context
  public static UserDTO createFrom(User userDetails) {
    return new UserDTO()
        .setUsername(userDetails.getUsername())
        .setFirstname(userDetails.getFirstname())
        .setLastname(userDetails.getLastname());
  }

  private static UserDTO createFrom(UserEntity userEntity) {
    return new UserDTO()
        .setUsername(userEntity.getUsername())
        .setFirstname(userEntity.getFirstname())
        .setLastname(userEntity.getLastname());
  }

  public static UserDTO buildFromJWTAuthenticationToken(
      final JwtAuthenticationToken authentication) {
    final String name = authentication.getName();
    if (ConversionUtils.stringIsEmpty(name)) {
      return createUserDTO(DEFAULT_USER);
    } else {
      return createUserDTO(name);
    }
  }

  public static UserDTO buildFromTokenAuthentication(
      TokenAuthentication tokenAuth, TasklistProperties tasklistProperties) {
    final Map<String, Claim> claims = tokenAuth.getClaims();
    String name = DEFAULT_USER;
    if (claims.containsKey(tasklistProperties.getAuth0().getNameKey())) {
      name = claims.get(tasklistProperties.getAuth0().getNameKey()).asString();
    }
    return createUserDTO(name);
  }

  public static UserDTO buildFromAuthentication(final Authentication authentication) {
    final Object principal = authentication.getPrincipal();
    if (principal instanceof User) {
      return UserDTO.createFrom((User) principal);
    } else {
      throw new TasklistRuntimeException(
          String.format("Could not build UserDTO from authentication principal %s", principal));
    }
  }

  public static UserDTO createUserDTO(String name) {
    return new UserDTO().setUsername(name).setFirstname(EMPTY).setLastname(name);
  }
}
