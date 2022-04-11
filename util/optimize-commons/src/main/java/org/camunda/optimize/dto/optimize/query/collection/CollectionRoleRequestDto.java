/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
public class CollectionRoleRequestDto {
  private static final String ID_SEGMENT_SEPARATOR = ":";

  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleRequestDto(final IdentityDto identity, final RoleType role) {
    setIdentity(identity);
    this.role = role;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(convertIdentityToRoleId(identity));
  }

  public void setIdentity(final IdentityDto identity) {
    this.id = convertIdentityToRoleId(identity);
    this.identity = identity;
  }

  private String convertIdentityToRoleId(final IdentityDto identity) {
    return identity.getType() == null
      ? "UNKNOWN" + ID_SEGMENT_SEPARATOR + identity.getId()
      : identity.getType().name() + ID_SEGMENT_SEPARATOR + identity.getId();
  }
}
