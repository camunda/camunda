/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AllArgsConstructor
public class DashboardRestMapper {

  private final AbstractIdentityService identityService;

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
