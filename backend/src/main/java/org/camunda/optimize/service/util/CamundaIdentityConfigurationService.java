/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import io.camunda.identity.sdk.IdentityConfiguration;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.camunda.optimize.service.util.configuration.security.CCSMAuthConfiguration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(CCSMCondition.class)
public class CamundaIdentityConfigurationService {
  private final ConfigurationService configurationService;

  public IdentityConfiguration getIdentityConfiguration() {
    final CCSMAuthConfiguration ccsmAuthConfig = configurationService.getAuthConfiguration().getCcsmAuthConfiguration();
    return new IdentityConfiguration.Builder()
      .withBaseUrl(ccsmAuthConfig.getBaseUrl())
      .withIssuer(ccsmAuthConfig.getIssuerUrl())
      .withIssuerBackendUrl(ccsmAuthConfig.getIssuerBackendUrl())
      .withClientId(ccsmAuthConfig.getClientId())
      .withClientSecret(ccsmAuthConfig.getClientSecret())
      .withAudience(ccsmAuthConfig.getAudience())
      .build();
  }

}
