/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;

import java.util.Optional;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class CollectionRoleDto {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleDto(final IdentityDto identity, final RoleType role) {
    this.id = convertIdentityToRoleId(identity);
    this.identity = identity;
    this.role = role;
  }

  public CollectionRoleDto(CollectionRoleDto oldRole) {
    this.id = convertIdentityToRoleId(this.identity);
    this.identity = new IdentityDto(oldRole.getIdentity().getId(), oldRole.getIdentity().getType());
    this.role = oldRole.role;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  private String convertIdentityToRoleId(final IdentityDto identity) {
    return identity.getType() == null
      ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
      : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }
}
