/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import org.springframework.security.core.userdetails.UserDetails;

public class UserDto {

  private static final String EMPTY = "";
  private String firstname;
  private String lastname;
  private boolean canLogout;

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

  public static UserDto fromUserDetails(UserDetails userDetails) {
    return new UserDto()
        .setFirstname("Demo")
        .setLastname("User")
        .setCanLogout(true);
  }
  
  public static UserDto fromName(String name) {
    return new UserDto()
        .setFirstname(EMPTY)
        .setLastname(name)
        .setCanLogout(false);
  }

}