/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.optimize.dto.optimize.UserDto;
import java.util.List;
import java.util.Objects;

public class UserResponseDto {

  @JsonUnwrapped private UserDto userDto;
  private List<AuthorizationType> authorizations;

  public UserResponseDto(final UserDto userDto, final List<AuthorizationType> authorizations) {
    this.userDto = userDto;
    this.authorizations = authorizations;
  }

  public UserResponseDto() {}

  public UserDto getUserDto() {
    return userDto;
  }

  @JsonUnwrapped
  public void setUserDto(final UserDto userDto) {
    this.userDto = userDto;
  }

  public List<AuthorizationType> getAuthorizations() {
    return authorizations;
  }

  public void setAuthorizations(final List<AuthorizationType> authorizations) {
    this.authorizations = authorizations;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof UserResponseDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UserResponseDto that = (UserResponseDto) o;
    return Objects.equals(userDto, that.userDto)
        && Objects.equals(authorizations, that.authorizations);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userDto, authorizations);
  }

  @Override
  public String toString() {
    return "UserResponseDto(userDto="
        + getUserDto()
        + ", authorizations="
        + getAuthorizations()
        + ")";
  }
}
