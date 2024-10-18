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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
