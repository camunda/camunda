/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db.indices;

import java.util.Objects;

public class UserTestDto {

  String username;
  String password;

  public UserTestDto() {}

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UserTestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserTestDto that = (UserTestDto) o;
    return Objects.equals(username, that.username) && Objects.equals(password, that.password);
  }

  @Override
  public String toString() {
    return "UserTestDto(username=" + getUsername() + ", password=" + getPassword() + ")";
  }
}
