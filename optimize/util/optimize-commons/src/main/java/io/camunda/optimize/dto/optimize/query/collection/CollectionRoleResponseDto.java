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
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionRoleResponseDto implements Comparable<CollectionRoleResponseDto> {

  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
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

  public enum Fields {
    id,
    identity,
    role
  }
}
