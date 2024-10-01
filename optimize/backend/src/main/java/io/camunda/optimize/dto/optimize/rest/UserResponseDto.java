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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $userDto = getUserDto();
    result = result * PRIME + ($userDto == null ? 43 : $userDto.hashCode());
    final Object $authorizations = getAuthorizations();
    result = result * PRIME + ($authorizations == null ? 43 : $authorizations.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof UserResponseDto)) {
      return false;
    }
    final UserResponseDto other = (UserResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$userDto = getUserDto();
    final Object other$userDto = other.getUserDto();
    if (this$userDto == null ? other$userDto != null : !this$userDto.equals(other$userDto)) {
      return false;
    }
    final Object this$authorizations = getAuthorizations();
    final Object other$authorizations = other.getAuthorizations();
    if (this$authorizations == null
        ? other$authorizations != null
        : !this$authorizations.equals(other$authorizations)) {
      return false;
    }
    return true;
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
