/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationDto;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.ui.HeaderLogoRetriever.readLogoAsBase64;


@Component
@Slf4j
@RequiredArgsConstructor
public class UIConfigurationService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final OptimizeVersionService versionService;
  // cached version
  private String logoAsBase64;

  public UIConfigurationDto getUIConfiguration() {
    UIConfigurationDto uiConfigurationDto = new UIConfigurationDto();
    uiConfigurationDto.setHeader(getHeaderCustomization());
    uiConfigurationDto.setEmailEnabled(configurationService.getEmailEnabled());
    uiConfigurationDto.setSharingEnabled(configurationService.getSharingEnabled());
    uiConfigurationDto.setOptimizeVersion(versionService.getRawVersion());
    uiConfigurationDto.setWebappsEndpoints(getCamundaWebappsEndpoints());
    return uiConfigurationDto;
  }

  private Map<String, WebappsEndpointDto> getCamundaWebappsEndpoints() {
    Map<String, WebappsEndpointDto> engineNameToEndpoints = new HashMap<>();
    for (Map.Entry<String, EngineConfiguration> entry : configurationService.getConfiguredEngines().entrySet()) {
      EngineConfiguration engineConfiguration = entry.getValue();
      WebappsEndpointDto webappsEndpoint = new WebappsEndpointDto();
      String endpointAsString = "";
      if (engineConfiguration.getWebapps().isEnabled()) {
        endpointAsString = engineConfiguration.getWebapps().getEndpoint();
      }
      webappsEndpoint.setEndpoint(endpointAsString);
      webappsEndpoint.setEngineName(engineConfiguration.getName());
      engineNameToEndpoints.put(entry.getKey(), webappsEndpoint);
    }
    return engineNameToEndpoints;
  }

  private HeaderCustomizationDto getHeaderCustomization() {
    UIConfiguration uiConfiguration = configurationService.getUiConfiguration();
    HeaderCustomization headerCustomization = uiConfiguration.getHeader();
    return new HeaderCustomizationDto(
      headerCustomization.getTextColor(),
      headerCustomization.getBackgroundColor(),
      getLogoAsBase64()
    );
  }

  private String getLogoAsBase64() {
    String pathToLogoIcon = configurationService.getUiConfiguration().getHeader().getPathToLogoIcon();
    if (logoAsBase64 == null) {
      this.logoAsBase64 = readLogoAsBase64(pathToLogoIcon);
    }
    return this.logoAsBase64;
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    this.logoAsBase64 = null;
  }
}
