/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AuthorizedEntityDto that = (AuthorizedEntityDto) o;
    return currentUserRole == that.currentUserRole;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(currentUserRole);
  }

  @Override
  public String toString() {
    return "AuthorizedEntityDto(currentUserRole=" + getCurrentUserRole() + ")";
  }
}
