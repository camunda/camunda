/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

public class CredentialsDto {

  protected String username;
  protected String password;

  public CredentialsDto() {}

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
    return other instanceof CredentialsDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $username = getUsername();
    result = result * PRIME + ($username == null ? 43 : $username.hashCode());
    final Object $password = getPassword();
    result = result * PRIME + ($password == null ? 43 : $password.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CredentialsDto)) {
      return false;
    }
    final CredentialsDto other = (CredentialsDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$username = getUsername();
    final Object other$username = other.getUsername();
    if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
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
    return "CredentialsDto(username=" + getUsername() + ", password=" + getPassword() + ")";
  }
}
