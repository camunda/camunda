/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

// supposed to be called from entity specific subclasses only
public class AuthorizedEntityDto {

  private RoleType currentUserRole;

  protected AuthorizedEntityDto(final RoleType currentUserRole) {
    this.currentUserRole = currentUserRole;
  }

  protected AuthorizedEntityDto() {}

  public RoleType getCurrentUserRole() {
    return currentUserRole;
  }

  public void setCurrentUserRole(final RoleType currentUserRole) {
    this.currentUserRole = currentUserRole;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedEntityDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $currentUserRole = getCurrentUserRole();
    result = result * PRIME + ($currentUserRole == null ? 43 : $currentUserRole.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedEntityDto)) {
      return false;
    }
    final AuthorizedEntityDto other = (AuthorizedEntityDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$currentUserRole = getCurrentUserRole();
    final Object other$currentUserRole = other.getCurrentUserRole();
    if (this$currentUserRole == null
        ? other$currentUserRole != null
        : !this$currentUserRole.equals(other$currentUserRole)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedEntityDto(currentUserRole=" + getCurrentUserRole() + ")";
  }
}
