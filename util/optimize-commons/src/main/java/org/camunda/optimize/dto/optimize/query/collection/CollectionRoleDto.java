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

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class CollectionRoleDto {
  @Setter(value = AccessLevel.PROTECTED)
  private String id;
  private IdentityDto identity;
  private RoleType role;

  public CollectionRoleDto(final IdentityDto identity, final RoleType role) {
    this.id = identity.getType().name() + ":" + identity.getId();
    this.identity = identity;
    this.role = role;
  }

}
