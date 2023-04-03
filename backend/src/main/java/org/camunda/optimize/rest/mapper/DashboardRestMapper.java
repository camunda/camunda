/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.mapper;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Optional;

@Slf4j
@Component
@AllArgsConstructor
public class DashboardRestMapper {

  private static final String TYPE_TEXT_VALUE = "text";
  public static final String TEXT_FIELD = "text";
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
        localizeTextsFromTextTiles(dashboardDefinition, validLocale);
      }
    }
  }

  private void localizeTextsFromTextTiles(final DashboardDefinitionRestDto dashboardData, final String locale) {
    dashboardData.getTiles().forEach(tile -> {
      if (tile.getType() == DashboardTileType.TEXT) {
        final HashMap<String, Object> textTileConfiguration = (HashMap<String, Object>) tile.getConfiguration();
        InstantPreviewDashboardService.
          findAndConvertTileContent(textTileConfiguration,
                                    TYPE_TEXT_VALUE,
                                    this::localizeTextFromTile,
                                    locale);
      }
    });
  }

  private void localizeTextFromTile(HashMap<String, Object> textTileConfiguration, String locale) {
    String textContent = (String) textTileConfiguration.get(TEXT_FIELD);
    Optional.ofNullable(localizationService.getLocalizationForInstantPreviewDashboardCode(
      locale,
      textContent
    )).ifPresent(localizedText -> textTileConfiguration.put(TEXT_FIELD, localizedText));
  }
}
