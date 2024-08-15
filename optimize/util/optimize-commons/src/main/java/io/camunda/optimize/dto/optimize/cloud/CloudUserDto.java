/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud;

import java.util.List;
import java.util.function.Supplier;

public class CloudUserDto {

  private String userId;
  private String name;
  private String email;
  private List<String> roles;

  public CloudUserDto() {}

  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(this::getUserId, this::getName, this::getEmail);
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(final List<String> roles) {
    this.roles = roles;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CloudUserDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $userId = getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    final Object $name = getName();
    result = result * PRIME + ($name == null ? 43 : $name.hashCode());
    final Object $email = getEmail();
    result = result * PRIME + ($email == null ? 43 : $email.hashCode());
    final Object $roles = getRoles();
    result = result * PRIME + ($roles == null ? 43 : $roles.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CloudUserDto)) {
      return false;
    }
    final CloudUserDto other = (CloudUserDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$userId = getUserId();
    final Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) {
      return false;
    }
    final Object this$name = getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
      return false;
    }
    final Object this$email = getEmail();
    final Object other$email = other.getEmail();
    if (this$email == null ? other$email != null : !this$email.equals(other$email)) {
      return false;
    }
    final Object this$roles = getRoles();
    final Object other$roles = other.getRoles();
    if (this$roles == null ? other$roles != null : !this$roles.equals(other$roles)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CloudUserDto(userId="
        + getUserId()
        + ", name="
        + getName()
        + ", email="
        + getEmail()
        + ", roles="
        + getRoles()
        + ")";
  }
}
