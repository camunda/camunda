/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

import java.util.Objects;

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
    return Objects.hash(password);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserCredentialsDto that = (UserCredentialsDto) o;
    return Objects.equals(password, that.password);
  }

  @Override
  public String toString() {
    return "UserCredentialsDto(password=" + getPassword() + ")";
  }
}
