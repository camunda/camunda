/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;

import io.camunda.operate.webapp.security.es.User;

public class UserDto {

  private String username;
  private String firstname;
  private String lastname;
  private boolean canLogout;

  public String getUsername() {
    return username;
  }

  public UserDto setUsername(final String username) {
    this.username = username;
    return this;
  }

  public String getFirstname() {
    return firstname;
  }

  public String getLastname() {
    return lastname;
  }

  public boolean isCanLogout() {
    return canLogout;
  }

  public UserDto setFirstname(String firstname) {
    this.firstname = firstname;
    return this;
  }

  public UserDto setLastname(String lastname) {
    this.lastname = lastname;
    return this;
  }

  public UserDto setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
    return this;
  }

  public static UserDto fromUser(User userDetails) {
    return new UserDto()
        .setUsername(userDetails.getUsername())
        .setFirstname(userDetails.getFirstname())
        .setLastname(userDetails.getLastname())
        .setCanLogout(userDetails.isCanLogout());
  }
  
}