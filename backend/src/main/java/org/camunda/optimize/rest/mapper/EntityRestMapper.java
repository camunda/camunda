/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class EntityRestMapper {

  private final AbstractIdentityService identityService;

  public void prepareRestResponse(final EntityResponseDto entityDto) {
    Optional.ofNullable(entityDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(entityDto::setOwner);
    Optional.ofNullable(entityDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(entityDto::setLastModifier);
  }

}
