/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.newwork;

import java.util.*;
import org.springframework.util.StringUtils;

public class UserServiceUserDto {

  private String userId;

  private String name;

  private String displayName;

  private String email;

  private boolean canLogout;

  public UserServiceUserDto setCanLogout(final boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public String getUserId() {
    return userId;
  }

  public UserServiceUserDto setUserId(final String userId) {
    this.userId = userId;
    return this;
  }

  public String getDisplayName() {
    if (!StringUtils.hasText(displayName)) {
      return userId;
    }
    return displayName;
  }

  public UserServiceUserDto setDisplayName(final String displayName) {
    this.displayName = displayName;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public UserServiceUserDto setEmail(final String email) {
    this.email = email;
    return this;
  }

  public String getName() {
    return name;
  }

  public UserServiceUserDto setName(final String name) {
    this.name = name;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, name, displayName, email, canLogout);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserServiceUserDto userServiceUserDto = (UserServiceUserDto) o;
    return canLogout == userServiceUserDto.canLogout
        && userId.equals(userServiceUserDto.userId)
        && name.equals(userServiceUserDto.name)
        && displayName.equals(userServiceUserDto.displayName)
        && email.equals(userServiceUserDto.email);
  }
}
