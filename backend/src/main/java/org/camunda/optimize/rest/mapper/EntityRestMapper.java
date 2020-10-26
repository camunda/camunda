/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.service.IdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class EntityRestMapper {
  private final IdentityService identityService;

  public void prepareRestResponse(final EntityResponseDto entityDto) {
    resolveOwnerAndModifierNames(entityDto);
  }

  private void resolveOwnerAndModifierNames(EntityResponseDto entityDto) {
    Optional.ofNullable(entityDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(entityDto::setOwner);
    Optional.ofNullable(entityDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(entityDto::setLastModifier);
  }
}
