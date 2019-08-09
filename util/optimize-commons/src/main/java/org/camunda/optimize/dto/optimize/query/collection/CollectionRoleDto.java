/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityDto;

import java.util.Optional;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public class CollectionRoleDto {
  private String id;
  private IdentityDto identity;
  private CollectionRole role;

  public CollectionRoleDto(final IdentityDto identity, final CollectionRole role) {
    this.identity = identity;
    this.role = role;
  }

  public String getId() {
    return Optional.ofNullable(id).orElse(identity.getType().name() + ":" + identity.getId());
  }
}
