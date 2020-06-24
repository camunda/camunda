/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.rest.dto;

import io.zeebe.tasklist.webapp.security.es.User;

public class UserDto {

  private String firstname;
  private String lastname;
  private boolean canLogout;

  public String getFirstname() {
    return firstname;
  }

  public UserDto setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public String getLastname() {
    return lastname;
  }

  public UserDto setLastname(String lastname) {
    this.lastname = lastname;
    return this;
  }

  public boolean isCanLogout() {
    return canLogout;
  }

  public UserDto setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public static UserDto fromUser(User userDetails) {
    return new UserDto()
        .setFirstname(userDetails.getFirstname())
        .setLastname(userDetails.getLastname())
        .setCanLogout(userDetails.isCanLogout());
  }
}
