/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.ui_configuration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UIConfigurationResponseDto {

  private boolean emailEnabled;
  private boolean sharingEnabled;
  private boolean tenantsAvailable;
  private String optimizeVersion;
  private boolean isEnterpriseMode;
  private String optimizeProfile;
  private Map<String, WebappsEndpointDto> webappsEndpoints;
  private Map<AppName, String> webappsLinks; // links for the app switcher
  private String notificationsUrl;
  private List<String> webhooks;
  private boolean logoutHidden;
  private Integer exportCsvLimit;

  // mirrors SettingsDto
  private boolean metadataTelemetryEnabled;

  // true if settingsDto flags are confirmed by superuser. If false, settings reflect initial config flags only
  private boolean settingsManuallyConfirmed;

  private MixpanelConfigResponseDto mixpanel = new MixpanelConfigResponseDto();

  private OnboardingResponseDto onboarding = new OnboardingResponseDto();
}
