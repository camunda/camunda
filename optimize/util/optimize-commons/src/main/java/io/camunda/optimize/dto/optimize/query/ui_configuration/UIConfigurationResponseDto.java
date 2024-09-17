/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

import io.camunda.optimize.service.util.configuration.DatabaseType;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UIConfigurationResponseDto {

  private boolean emailEnabled;
  private boolean sharingEnabled;
  private boolean tenantsAvailable;
  private boolean userSearchAvailable;
  private boolean userTaskAssigneeAnalyticsEnabled;
  private String optimizeVersion;
  private String optimizeDocsVersion;
  private boolean isEnterpriseMode;
  private OptimizeProfile optimizeProfile;
  private Map<String, WebappsEndpointDto> webappsEndpoints;
  private Map<AppName, String> webappsLinks; // links for the app switcher
  private String notificationsUrl;
  private List<String> webhooks;
  private boolean logoutHidden;
  private int maxNumDataSourcesForReport;
  private Integer exportCsvLimit;
  private DatabaseType optimizeDatabase;
  private boolean validLicense;
  private String licenseType;

  private MixpanelConfigResponseDto mixpanel = new MixpanelConfigResponseDto();

  private OnboardingResponseDto onboarding = new OnboardingResponseDto();

  public UIConfigurationResponseDto(
      boolean emailEnabled,
      boolean sharingEnabled,
      boolean tenantsAvailable,
      boolean userSearchAvailable,
      boolean userTaskAssigneeAnalyticsEnabled,
      String optimizeVersion,
      String optimizeDocsVersion,
      boolean isEnterpriseMode,
      OptimizeProfile optimizeProfile,
      Map<String, WebappsEndpointDto> webappsEndpoints,
      Map<AppName, String> webappsLinks,
      String notificationsUrl,
      List<String> webhooks,
      boolean logoutHidden,
      int maxNumDataSourcesForReport,
      Integer exportCsvLimit,
      DatabaseType optimizeDatabase,
      boolean validLicense,
      String licenseType,
      MixpanelConfigResponseDto mixpanel,
      OnboardingResponseDto onboarding) {
    this.emailEnabled = emailEnabled;
    this.sharingEnabled = sharingEnabled;
    this.tenantsAvailable = tenantsAvailable;
    this.userSearchAvailable = userSearchAvailable;
    this.userTaskAssigneeAnalyticsEnabled = userTaskAssigneeAnalyticsEnabled;
    this.optimizeVersion = optimizeVersion;
    this.optimizeDocsVersion = optimizeDocsVersion;
    this.isEnterpriseMode = isEnterpriseMode;
    this.optimizeProfile = optimizeProfile;
    this.webappsEndpoints = webappsEndpoints;
    this.webappsLinks = webappsLinks;
    this.notificationsUrl = notificationsUrl;
    this.webhooks = webhooks;
    this.logoutHidden = logoutHidden;
    this.maxNumDataSourcesForReport = maxNumDataSourcesForReport;
    this.exportCsvLimit = exportCsvLimit;
    this.optimizeDatabase = optimizeDatabase;
    this.validLicense = validLicense;
    this.licenseType = licenseType;
    this.mixpanel = mixpanel;
    this.onboarding = onboarding;
  }

  public UIConfigurationResponseDto() {}
}
