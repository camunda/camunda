/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.RoleType;
import java.util.Objects;

public class CollectionRoleUpdateRequestDto {

  private RoleType role;

  public CollectionRoleUpdateRequestDto(final RoleType role) {
    this.role = role;
  }

  protected CollectionRoleUpdateRequestDto() {}

  public RoleType getRole() {
    return role;
  }

  public void setRole(final RoleType role) {
    this.role = role;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionRoleUpdateRequestDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(role);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CollectionRoleUpdateRequestDto that = (CollectionRoleUpdateRequestDto) o;
    return Objects.equals(role, that.role);
  }

  @Override
  public String toString() {
    return "CollectionRoleUpdateRequestDto(role=" + getRole() + ")";
  }

  public enum Fields {
    role
  }
}
