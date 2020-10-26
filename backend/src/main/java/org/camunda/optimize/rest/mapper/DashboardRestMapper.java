/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.service.IdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class DashboardRestMapper {
  private final IdentityService identityService;

  public void prepareRestResponse(final AuthorizedDashboardDefinitionResponseDto dashboardDefinitionDto) {
    resolveOwnerAndModifierNames(dashboardDefinitionDto.getDefinitionDto());
  }

  public void prepareRestResponse(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    resolveOwnerAndModifierNames(dashboardDefinitionDto);
  }

  private void resolveOwnerAndModifierNames(DashboardDefinitionRestDto dashboardDefinitionDto) {
    Optional.ofNullable(dashboardDefinitionDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(dashboardDefinitionDto::setOwner);
    Optional.ofNullable(dashboardDefinitionDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(dashboardDefinitionDto::setLastModifier);
  }
}
