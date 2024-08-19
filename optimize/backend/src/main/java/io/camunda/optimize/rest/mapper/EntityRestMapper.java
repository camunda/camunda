/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EntityRestMapper {

  private final AbstractIdentityService identityService;

  public EntityRestMapper(final AbstractIdentityService identityService) {
    this.identityService = identityService;
  }

  public void prepareRestResponse(final EntityResponseDto entityDto) {
    Optional.ofNullable(entityDto.getOwner())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(entityDto::setOwner);
    Optional.ofNullable(entityDto.getLastModifier())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(entityDto::setLastModifier);
  }
}
