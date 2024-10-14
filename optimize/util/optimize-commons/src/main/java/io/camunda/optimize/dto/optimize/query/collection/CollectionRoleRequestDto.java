/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import java.util.Optional;

public class CollectionRoleRequestDto {

  private static final String ID_SEGMENT_SEPARATOR = ":";
  private String id;
  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleRequestDto(final IdentityDto identity, final RoleType role) {
    setIdentity(identity);
    this.role = role;
  }

  protected CollectionRoleRequestDto() {}

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  protected void setId(final String id) {
    this.id = id;
  }

  private String convertIdentityToRoleId(final IdentityDto identity) {
    return identity.getType() == null
        ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
        : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public IdentityDto getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityDto identity) {
    id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  public RoleType getRole() {
    return role;
  }

  public void setRole(final RoleType role) {
    this.role = role;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionRoleRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $identity = getIdentity();
    result = result * PRIME + ($identity == null ? 43 : $identity.hashCode());
    final Object $role = getRole();
    result = result * PRIME + ($role == null ? 43 : $role.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CollectionRoleRequestDto)) {
      return false;
    }
    final CollectionRoleRequestDto other = (CollectionRoleRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$identity = getIdentity();
    final Object other$identity = other.getIdentity();
    if (this$identity == null ? other$identity != null : !this$identity.equals(other$identity)) {
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
    return "CollectionRoleRequestDto(id="
        + getId()
        + ", identity="
        + getIdentity()
        + ", role="
        + getRole()
        + ")";
  }

  public enum Fields {
    id,
    identity,
    role
  }
}
