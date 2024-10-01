/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.UserDto;
import org.apache.commons.lang3.StringUtils;

public class CollectionRoleResponseDto implements Comparable<CollectionRoleResponseDto> {

  private static final String ID_SEGMENT_SEPARATOR = ":";
  private String id;
  private IdentityWithMetadataResponseDto identity;
  private RoleType role;

  public CollectionRoleResponseDto(final CollectionRoleResponseDto oldRole) {
    if (oldRole.getIdentity().getType().equals(IdentityType.USER)) {
      final UserDto oldUserDto = (UserDto) oldRole.getIdentity();
      identity =
          new UserDto(
              oldUserDto.getId(),
              oldUserDto.getFirstName(),
              oldUserDto.getLastName(),
              oldUserDto.getEmail());
    } else {
      final GroupDto oldGroupDto = (GroupDto) oldRole.getIdentity();
      identity =
          new GroupDto(oldGroupDto.getId(), oldGroupDto.getName(), oldGroupDto.getMemberCount());
    }

    role = oldRole.role;
    id = convertIdentityToRoleId(identity);
  }

  public CollectionRoleResponseDto(
      final IdentityWithMetadataResponseDto identity, final RoleType role) {
    this.identity = identity;
    id = convertIdentityToRoleId(this.identity);
    this.role = role;
  }

  protected CollectionRoleResponseDto() {}

  @Override
  public int compareTo(final CollectionRoleResponseDto other) {
    if (identity instanceof UserDto && other.getIdentity() instanceof GroupDto) {
      return 1;
    } else if (identity instanceof GroupDto && other.getIdentity() instanceof UserDto) {
      return -1;
    } else {
      return StringUtils.compareIgnoreCase(identity.getName(), other.getIdentity().getName());
    }
  }

  private String convertIdentityToRoleId(final IdentityWithMetadataResponseDto identity) {
    return identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }

  public static <T extends IdentityWithMetadataResponseDto> CollectionRoleResponseDto from(
      final CollectionRoleRequestDto roleDto, final T identityWithMetaData) {
    return new CollectionRoleResponseDto(identityWithMetaData, roleDto.getRole());
  }

  public String getId() {
    return id;
  }

  protected void setId(final String id) {
    this.id = id;
  }

  public IdentityWithMetadataResponseDto getIdentity() {
    return identity;
  }

  public void setIdentity(final IdentityWithMetadataResponseDto identity) {
    this.identity = identity;
  }

  public RoleType getRole() {
    return role;
  }

  public void setRole(final RoleType role) {
    this.role = role;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CollectionRoleResponseDto;
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
    if (!(o instanceof CollectionRoleResponseDto)) {
      return false;
    }
    final CollectionRoleResponseDto other = (CollectionRoleResponseDto) o;
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
    return "CollectionRoleResponseDto(id="
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
