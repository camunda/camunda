/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static io.camunda.optimize.service.util.configuration.OptimizeProfile.CCSM;
import static io.camunda.optimize.service.util.configuration.OptimizeProfile.CLOUD;

import io.camunda.identity.sdk.Identity;
import io.camunda.optimize.dto.optimize.query.ui_configuration.MixpanelConfigResponseDto;
import io.camunda.optimize.dto.optimize.query.ui_configuration.OnboardingResponseDto;
import io.camunda.optimize.dto.optimize.query.ui_configuration.UIConfigurationResponseDto;
import io.camunda.optimize.license.LicenseType;
import io.camunda.optimize.rest.cloud.CloudSaasMetaInfoService;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.metadata.OptimizeVersionService;
import io.camunda.optimize.service.tenant.TenantService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class UIConfigurationService {

  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
  private final ConfigurationService configurationService;
  private final OptimizeVersionService versionService;
  private final TenantService tenantService;
  private final SettingsService settingService;
  private final CamundaLicenseService camundaLicenseService;
  private final Environment environment;
  // optional as it is only available conditionally, see implementations of the interface
  private final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService;
  private final Identity identity;

  public UIConfigurationService(
      final ConfigurationService configurationService,
      final OptimizeVersionService versionService,
      final TenantService tenantService,
      final SettingsService settingService,
      final CamundaLicenseService camundaLicenseService,
      final Environment environment,
      final Optional<CloudSaasMetaInfoService> cloudSaasMetaInfoService,
      final Identity identity) {
    this.configurationService = configurationService;
    this.versionService = versionService;
    this.tenantService = tenantService;
    this.settingService = settingService;
    this.camundaLicenseService = camundaLicenseService;
    this.environment = environment;
    this.cloudSaasMetaInfoService = cloudSaasMetaInfoService;
    this.identity = identity;
  }

  public UIConfigurationResponseDto getUIConfiguration() {
    final UIConfigurationResponseDto uiConfigurationDto = new UIConfigurationResponseDto();
    uiConfigurationDto.setLogoutHidden(configurationService.getUiConfiguration().isLogoutHidden());
    uiConfigurationDto.setEmailEnabled(configurationService.getEmailEnabled());
    uiConfigurationDto.setSharingEnabled(
        settingService.getSettings().getSharingEnabled().orElse(false));
    uiConfigurationDto.setTenantsAvailable(tenantService.isMultiTenantEnvironment());
    uiConfigurationDto.setOptimizeVersion(versionService.getRawVersion());
    uiConfigurationDto.setOptimizeDocsVersion(versionService.getDocsVersion());
    final OptimizeProfile optimizeProfile = ConfigurationService.getOptimizeProfile(environment);
    uiConfigurationDto.setEnterpriseMode(isEnterpriseMode(optimizeProfile));
    uiConfigurationDto.setUserSearchAvailable(isUserSearchAvailable(optimizeProfile));
    uiConfigurationDto.setUserTaskAssigneeAnalyticsEnabled(
        configurationService.getUiConfiguration().isUserTaskAssigneeAnalyticsEnabled());
    uiConfigurationDto.setOptimizeProfile(optimizeProfile);
    uiConfigurationDto.setExportCsvLimit(
        configurationService.getCsvConfiguration().getExportCsvLimit());
    uiConfigurationDto.setMaxNumDataSourcesForReport(
        configurationService.getUiConfiguration().getMaxNumDataSourcesForReport());
    uiConfigurationDto.setOptimizeDatabase(ConfigurationService.getDatabaseType(environment));
    uiConfigurationDto.setValidLicense(isCamundaLicenseValid());
    uiConfigurationDto.setLicenseType(getLicenseType().getName());
    uiConfigurationDto.setCommercial(isCommercialCamundaLicense());
    final OffsetDateTime expirationDate = getCamundaLicenseExpiresAt();
    uiConfigurationDto.setExpiresAt(
        expirationDate == null ? null : DATE_TIME_FORMATTER.format(getCamundaLicenseExpiresAt()));

    final MixpanelConfigResponseDto mixpanel = uiConfigurationDto.getMixpanel();
    mixpanel.setEnabled(configurationService.getAnalytics().isEnabled());
    mixpanel.setApiHost(configurationService.getAnalytics().getMixpanel().getApiHost());
    mixpanel.setToken(configurationService.getAnalytics().getMixpanel().getToken());
    mixpanel.setOrganizationId(
        configurationService.getAnalytics().getMixpanel().getProperties().getOrganizationId());
    mixpanel.setOsanoScriptUrl(
        configurationService.getAnalytics().getOsano().getScriptUrl().orElse(null));
    mixpanel.setStage(configurationService.getAnalytics().getMixpanel().getProperties().getStage());
    mixpanel.setClusterId(
        configurationService.getAnalytics().getMixpanel().getProperties().getClusterId());

    final OnboardingResponseDto onboarding = uiConfigurationDto.getOnboarding();
    onboarding.setEnabled(configurationService.getOnboarding().isEnabled());
    onboarding.setAppCuesScriptUrl(configurationService.getOnboarding().getAppCuesScriptUrl());
    onboarding.setOrgId(configurationService.getOnboarding().getProperties().getOrganizationId());
    onboarding.setClusterId(configurationService.getOnboarding().getProperties().getClusterId());

    cloudSaasMetaInfoService
        .flatMap(CloudSaasMetaInfoService::getSalesPlanType)
        .ifPresent(onboarding::setSalesPlanType);
    cloudSaasMetaInfoService.ifPresent(
        service -> {
          uiConfigurationDto.setWebappsLinks(service.getWebappsLinks());
          uiConfigurationDto.setNotificationsUrl(
              configurationService.getPanelNotificationConfiguration().getUrl());
        });

    return uiConfigurationDto;
  }

  private boolean isCamundaLicenseValid() {
    return camundaLicenseService.isCamundaLicenseValid();
  }

  private LicenseType getLicenseType() {
    return camundaLicenseService.getCamundaLicenseType();
  }

  private boolean isCommercialCamundaLicense() {
    return camundaLicenseService.isCommercialCamundaLicense();
  }

  private OffsetDateTime getCamundaLicenseExpiresAt() {
    return camundaLicenseService.getCamundaLicenseExpiresAt();
  }

  private boolean isEnterpriseMode(final OptimizeProfile optimizeProfile) {
    if (optimizeProfile.equals(CLOUD)) {
      return true;
    } else if (optimizeProfile.equals(CCSM)) {
      return configurationService.getSecurityConfiguration().getLicense().isEnterprise();
    }
    throw new OptimizeConfigurationException(
        "Could not determine whether Optimize is running in enterprise mode");
  }

  private boolean isUserSearchAvailable(final OptimizeProfile optimizeProfile) {
    return !CCSM.equals(optimizeProfile) || identity.users().isAvailable();
  }
}
