/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import io.camunda.auth.spring.config.AuthenticationConfiguration;
import io.camunda.auth.spring.config.OidcAuthenticationConfiguration;
import io.camunda.auth.spring.config.ProvidersConfiguration;
import io.camunda.auth.spring.config.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;

public class OidcAuthenticationConfigurationRepository {

  private static final String DEFAULT_REGISTRATION_ID = "camunda";
  private final Map<String, OidcAuthenticationConfiguration> configurations;

  public OidcAuthenticationConfigurationRepository(
      final SecurityConfiguration securityConfiguration) {
    configurations = new HashMap<>();
    final AuthenticationConfiguration authConfig = securityConfiguration.getAuthentication();

    if (authConfig.getOidc() != null && authConfig.getOidc().isSet()) {
      configurations.put(DEFAULT_REGISTRATION_ID, authConfig.getOidc());
    }

    final ProvidersConfiguration providers = authConfig.getProviders();
    if (providers != null && providers.getOidc() != null) {
      configurations.putAll(providers.getOidc());
    }
  }

  public Map<String, OidcAuthenticationConfiguration> getOidcAuthenticationConfigurations() {
    return Map.copyOf(configurations);
  }
}
