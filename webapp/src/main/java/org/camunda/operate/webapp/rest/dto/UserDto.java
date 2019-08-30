/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

import org.springframework.security.core.userdetails.UserDetails;

public class UserDto {

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

  public void setFirstname(String firstname) {
    this.firstname = firstname;
  }

  public void setLastname(String lastname) {
    this.lastname = lastname;
  }

  public void setCanLogout(boolean canLogout) {
    this.canLogout = canLogout;
  }

  public static UserDto fromUserDetails(UserDetails userDetails) {
    UserDto dto = new UserDto();
    dto.firstname = "Trial";
    dto.lastname = "License";
    dto.canLogout = true;
    return dto;
  }

}