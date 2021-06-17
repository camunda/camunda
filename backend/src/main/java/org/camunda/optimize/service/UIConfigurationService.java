/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.camunda.optimize.service.util.configuration.ui.UIConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.ui.HeaderLogoRetriever.readLogoAsBase64;

@Component
@Slf4j
@RequiredArgsConstructor
public class UIConfigurationService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final OptimizeVersionService versionService;
  private final TenantService tenantService;
  private final SettingsService settingService;
  @Autowired
  private final Environment environment;

  // cached version
  private String logoAsBase64;

  public UIConfigurationResponseDto getUIConfiguration() {
    UIConfigurationResponseDto uiConfigurationDto = new UIConfigurationResponseDto();
    uiConfigurationDto.setHeader(getHeaderCustomization());
    uiConfigurationDto.setLogoutHidden(configurationService.getUiConfiguration().isLogoutHidden());
    uiConfigurationDto.setEmailEnabled(configurationService.getEmailEnabled());
    uiConfigurationDto.setSharingEnabled(configurationService.getSharingEnabled());
    uiConfigurationDto.setTenantsAvailable(tenantService.isMultiTenantEnvironment());
    uiConfigurationDto.setOptimizeVersion(versionService.getRawVersion());
    uiConfigurationDto.setOptimizeCloudEnvironment(
      Arrays.asList(environment.getActiveProfiles()).contains(ConfigurationServiceConstants.CLOUD_PROFILE));
    uiConfigurationDto.setWebappsEndpoints(getCamundaWebappsEndpoints());
    uiConfigurationDto.setWebhooks(getConfiguredWebhooks());

    final SettingsResponseDto settings = settingService.getSettings();
    uiConfigurationDto.setMetadataTelemetryEnabled(settings.isMetadataTelemetryEnabled());
    uiConfigurationDto.setSettingsManuallyConfirmed(settings.isManuallyConfirmed());

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

  private List<String> getConfiguredWebhooks() {
    List<String> sortedWebhooksList = Lists.newArrayList(configurationService.getConfiguredWebhooks().keySet());
    sortedWebhooksList.sort(String.CASE_INSENSITIVE_ORDER);
    return sortedWebhooksList;
  }

  private HeaderCustomizationDto getHeaderCustomization() {
    HeaderCustomization headerCustomization = configurationService.getUiConfiguration().getHeader();
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
