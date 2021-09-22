/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;
import io.camunda.operate.webapp.security.Permission;
import java.util.List;

public class UserDto {

  private String username;
  private String firstname;
  private String lastname;
  private boolean canLogout;

  private List<Permission> permissions;

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

  public List<Permission> getPermissions() {
    return permissions;
  }

  public UserDto setPermissions(final List<Permission> permissions) {
    this.permissions = permissions;
    return this;
  }

}
