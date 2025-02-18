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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
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
}
