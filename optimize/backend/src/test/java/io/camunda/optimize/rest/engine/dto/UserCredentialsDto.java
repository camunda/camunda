/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

public class UserCredentialsDto {

  private String password;

  public UserCredentialsDto(final String password) {
    this.password = password;
  }

  protected UserCredentialsDto() {}

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UserCredentialsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $password = getPassword();
    result = result * PRIME + ($password == null ? 43 : $password.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserCredentialsDto)) {
      return false;
    }
    final UserCredentialsDto other = (UserCredentialsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$password = getPassword();
    final Object other$password = other.getPassword();
    if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "UserCredentialsDto(password=" + getPassword() + ")";
  }
}
