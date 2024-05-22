/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.ui_configuration;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.util.configuration.DatabaseType;
import org.camunda.optimize.service.util.configuration.OptimizeProfile;

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
  private Map<String, WebappsEndpointDto> webappsEndpoints;
  private Map<AppName, String> webappsLinks; // links for the app switcher
  private String notificationsUrl;
  private List<String> webhooks;
  private boolean logoutHidden;
  private int maxNumDataSourcesForReport;
  private Integer exportCsvLimit;
  private DatabaseType optimizeDatabase;

  private MixpanelConfigResponseDto mixpanel = new MixpanelConfigResponseDto();

  private OnboardingResponseDto onboarding = new OnboardingResponseDto();
}
