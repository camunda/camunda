/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Data
public class AuthorizedSimpleCollectionDefinitionDto extends AuthorizedEntityDto {
  @JsonUnwrapped
  private SimpleCollectionDefinitionDto definitionDto;

  public AuthorizedSimpleCollectionDefinitionDto(final RoleType currentUserRole,
                                                 final SimpleCollectionDefinitionDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  public EntityDto toEntityDto() {
    return definitionDto.toEntityDto(getCurrentUserRole());
  }

}
