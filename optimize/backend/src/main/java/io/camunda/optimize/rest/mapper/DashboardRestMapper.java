/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.mapper;

import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DashboardRestMapper {

  public static final String TEXT_FIELD = "text";
  private static final String TYPE_TEXT_VALUE = "text";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DashboardRestMapper.class);
  private final AbstractIdentityService identityService;
  private final LocalizationService localizationService;

  public DashboardRestMapper(
      final AbstractIdentityService identityService,
      final LocalizationService localizationService) {
    this.identityService = identityService;
    this.localizationService = localizationService;
  }

  public void prepareRestResponse(
      final AuthorizedDashboardDefinitionResponseDto dashboardDefinitionDto, final String locale) {
    prepareRestResponse(dashboardDefinitionDto.getDefinitionDto(), locale);
  }

  public void prepareRestResponse(
      final DashboardDefinitionRestDto dashboardDefinitionDto, final String locale) {
    prepareRestResponse(dashboardDefinitionDto, locale, false);
  }

  public void prepareRestResponse(
      final DashboardDefinitionRestDto dashboardDefinitionDto,
      final String locale,
      final boolean skipNameResolution) {
    if (!skipNameResolution) {
      resolveOwnerAndModifierNames(dashboardDefinitionDto);
    }
    localizeDashboard(dashboardDefinitionDto, locale);
  }

  private void resolveOwnerAndModifierNames(
      final DashboardDefinitionRestDto dashboardDefinitionDto) {
    Optional.ofNullable(dashboardDefinitionDto.getOwner())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(dashboardDefinitionDto::setOwner);
    Optional.ofNullable(dashboardDefinitionDto.getLastModifier())
        .flatMap(identityService::getIdentityNameById)
        .ifPresent(dashboardDefinitionDto::setLastModifier);
  }

  private void localizeDashboard(
      final DashboardDefinitionRestDto dashboardDefinition, final String locale) {
    if (dashboardDefinition.isManagementDashboard()
        || dashboardDefinition.isInstantPreviewDashboard()) {
      final String validLocale = localizationService.validateAndReturnValidLocale(locale);
      if (dashboardDefinition.isManagementDashboard()) {
        Optional.ofNullable(
                localizationService.getLocalizationForManagementDashboardCode(
                    validLocale, dashboardDefinition.getName()))
            .ifPresent(dashboardDefinition::setName);
        Optional.ofNullable(
                localizationService.getLocalizationForManagementDashboardCode(
                    validLocale, dashboardDefinition.getDescription()))
            .ifPresent(dashboardDefinition::setDescription);
      } else {
        Optional.ofNullable(
                localizationService.getLocalizationForInstantPreviewDashboardCode(
                    validLocale, dashboardDefinition.getName()))
            .ifPresent(dashboardDefinition::setName);
        Optional.ofNullable(
                localizationService.getLocalizationForInstantPreviewDashboardCode(
                    validLocale, dashboardDefinition.getDescription()))
            .ifPresent(dashboardDefinition::setDescription);
        localizeTextsFromTextTiles(dashboardDefinition, validLocale);
      }
    }
  }

  private void localizeTextsFromTextTiles(
      final DashboardDefinitionRestDto dashboardData, final String locale) {
    dashboardData
        .getTiles()
        .forEach(
            tile -> {
              if (tile.getType() == DashboardTileType.TEXT) {
                final Map<String, Object> textTileConfiguration =
                    (Map<String, Object>) tile.getConfiguration();
                InstantPreviewDashboardService.findAndConvertTileContent(
                    textTileConfiguration, TYPE_TEXT_VALUE, this::localizeTextFromTile, locale);
              }
            });
  }

  private void localizeTextFromTile(
      final Map<String, Object> textTileConfiguration, final String locale) {
    final String textContent = (String) textTileConfiguration.get(TEXT_FIELD);
    Optional.ofNullable(
            localizationService.getLocalizationForInstantPreviewDashboardCode(locale, textContent))
        .ifPresent(localizedText -> textTileConfiguration.put(TEXT_FIELD, localizedText));
  }
}
