/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.RoleType;

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
    final int PRIME = 59;
    int result = 1;
    final Object $role = getRole();
    result = result * PRIME + ($role == null ? 43 : $role.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CollectionRoleUpdateRequestDto)) {
      return false;
    }
    final CollectionRoleUpdateRequestDto other = (CollectionRoleUpdateRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$role = getRole();
    final Object other$role = other.getRole();
    if (this$role == null ? other$role != null : !this$role.equals(other$role)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CollectionRoleUpdateRequestDto(role=" + getRole() + ")";
  }

  public enum Fields {
    role
  }
}
