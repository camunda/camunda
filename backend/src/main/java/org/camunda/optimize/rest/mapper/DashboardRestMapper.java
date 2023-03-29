/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@AllArgsConstructor
public class DashboardRestMapper {

  private final AbstractIdentityService identityService;
  private final LocalizationService localizationService;

  public void prepareRestResponse(final AuthorizedDashboardDefinitionResponseDto dashboardDefinitionDto, final String locale) {
    prepareRestResponse(dashboardDefinitionDto.getDefinitionDto(), locale);
  }

  public void prepareRestResponse(final DashboardDefinitionRestDto dashboardDefinitionDto, final String locale) {
    resolveOwnerAndModifierNames(dashboardDefinitionDto);
    localizeDashboard(dashboardDefinitionDto, locale);
  }

  private void resolveOwnerAndModifierNames(DashboardDefinitionRestDto dashboardDefinitionDto) {
    Optional.ofNullable(dashboardDefinitionDto.getOwner())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(dashboardDefinitionDto::setOwner);
    Optional.ofNullable(dashboardDefinitionDto.getLastModifier())
      .flatMap(identityService::getIdentityNameById)
      .ifPresent(dashboardDefinitionDto::setLastModifier);
  }

  private void localizeDashboard(final DashboardDefinitionRestDto dashboardDefinition, final String locale) {
    if (dashboardDefinition.isManagementDashboard() || dashboardDefinition.isInstantPreviewDashboard()) {
      final String validLocale = localizationService.validateAndReturnValidLocale(locale);
      if (dashboardDefinition.isManagementDashboard()) {
        Optional.ofNullable(localizationService.getLocalizationForManagementDashboardCode(
          validLocale,
          dashboardDefinition.getName()
        )).ifPresent(dashboardDefinition::setName);
      } else {
        Optional.ofNullable(localizationService.getLocalizationForInstantPreviewDashboardCode(
          validLocale,
          dashboardDefinition.getName()
        )).ifPresent(dashboardDefinition::setName);
      }
    }
  }
}
