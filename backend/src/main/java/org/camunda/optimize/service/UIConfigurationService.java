/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.HeaderCustomizationDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.MixpanelConfigResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.OnboardingResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import org.camunda.optimize.dto.optimize.query.ui_configuration.WebappsEndpointDto;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.metadata.OptimizeVersionService;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.camunda.optimize.service.util.configuration.ui.HeaderCustomization;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CLOUD_PROFILE;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.PLATFORM_PROFILE;
import static org.camunda.optimize.service.util.configuration.ui.HeaderLogoRetriever.readLogoAsBase64;

@Component
@Slf4j
@RequiredArgsConstructor
public class UIConfigurationService implements ConfigurationReloadable {

  private final ConfigurationService configurationService;
  private final OptimizeVersionService versionService;
  private final TenantService tenantService;
  private final SettingsService settingService;
  private final Environment environment;

  // cached version
  private String logoAsBase64;

  public UIConfigurationResponseDto getUIConfiguration() {
    final UIConfigurationResponseDto uiConfigurationDto = new UIConfigurationResponseDto();
    uiConfigurationDto.setHeader(getHeaderCustomization());
    uiConfigurationDto.setLogoutHidden(configurationService.getUiConfiguration().isLogoutHidden());
    uiConfigurationDto.setEmailEnabled(configurationService.getEmailEnabled());
    uiConfigurationDto.setSharingEnabled(settingService.getSettings().getSharingEnabled().orElse(false));
    uiConfigurationDto.setTenantsAvailable(tenantService.isMultiTenantEnvironment());
    uiConfigurationDto.setOptimizeVersion(versionService.getRawVersion());
    final String optimizeProfile = determineOptimizeProfile();
    uiConfigurationDto.setEnterpriseMode(isEnterpriseMode(optimizeProfile));
    uiConfigurationDto.setOptimizeProfile(optimizeProfile);
    uiConfigurationDto.setWebappsEndpoints(getCamundaWebappsEndpoints());
    uiConfigurationDto.setWebhooks(getConfiguredWebhooks());
    uiConfigurationDto.setExportCsvLimit(configurationService.getExportCsvLimit());

    final SettingsResponseDto settings = settingService.getSettings();
    uiConfigurationDto.setMetadataTelemetryEnabled(settings.getMetadataTelemetryEnabled().orElse(true));
    uiConfigurationDto.setSettingsManuallyConfirmed(settings.isTelemetryManuallyConfirmed());

    final MixpanelConfigResponseDto mixpanel = uiConfigurationDto.getMixpanel();
    mixpanel.setEnabled(configurationService.getAnalytics().isEnabled());
    mixpanel.setApiHost(configurationService.getAnalytics().getMixpanel().getApiHost());
    mixpanel.setToken(configurationService.getAnalytics().getMixpanel().getToken());
    mixpanel.setOrganizationId(configurationService.getAnalytics().getMixpanel().getProperties().getOrganizationId());
    mixpanel.setOsanoScriptUrl(configurationService.getAnalytics().getOsano().getScriptUrl().orElse(null));
    mixpanel.setStage(configurationService.getAnalytics().getMixpanel().getProperties().getStage());
    mixpanel.setClusterId(configurationService.getAnalytics().getMixpanel().getProperties().getClusterId());

    final OnboardingResponseDto onboarding = uiConfigurationDto.getOnboarding();
    onboarding.setEnabled(configurationService.getOnboarding().isEnabled());
    onboarding.setAppCuesScriptUrl(configurationService.getOnboarding().getAppCuesScriptUrl());
    onboarding.setOrgId(configurationService.getOnboarding().getProperties().getOrganizationId());
    onboarding.setClusterId(configurationService.getOnboarding().getProperties().getClusterId());

    return uiConfigurationDto;
  }

  public boolean isEnterpriseMode() {
    return isEnterpriseMode(determineOptimizeProfile());
  }

  private boolean isEnterpriseMode(final String optimizeProfile) {
    if (Arrays.asList(CLOUD_PROFILE, PLATFORM_PROFILE).contains(optimizeProfile)) {
      return true;
    } else if (optimizeProfile.equals(CCSM_PROFILE)) {
      return configurationService.getSecurityConfiguration().getLicense().isEnterprise();
    }
    throw new OptimizeConfigurationException("Could not determine whether Optimize is running in enterprise mode");
  }

  private String determineOptimizeProfile() {
    final String[] activeProfiles = environment.getActiveProfiles();
    if (activeProfiles.length == 0) {
      return PLATFORM_PROFILE;
    }
    if (activeProfiles.length > 1) {
      throw new OptimizeConfigurationException("Cannot configure more than one profile for Optimize");
    }
    if (!Arrays.asList(CLOUD_PROFILE, CCSM_PROFILE, PLATFORM_PROFILE).contains(activeProfiles[0])) {
      throw new OptimizeConfigurationException("Invalid profile configured");
    }
    return activeProfiles[0];
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
