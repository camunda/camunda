/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.ProvidersConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OidcAuthenticationConfigurationRepository {

  public static final String REGISTRATION_ID = "oidc";
  private final Map<String, OidcAuthenticationConfiguration> providers;

  public OidcAuthenticationConfigurationRepository(
      final SecurityConfiguration securityConfiguration) {
    providers = initializeProviders(securityConfiguration);
  }

  protected Map<String, OidcAuthenticationConfiguration> initializeProviders(
      final SecurityConfiguration securityConfiguration) {
    final var providers = new HashMap<String, OidcAuthenticationConfiguration>();
    final var authenticationConfiguration = securityConfiguration.getAuthentication();

    Optional.ofNullable(authenticationConfiguration.getOidc())
        .filter(c -> Objects.nonNull(c.getClientId()) && !c.getClientId().isBlank())
        .ifPresent(c -> providers.put(REGISTRATION_ID, c));

    Optional.ofNullable(authenticationConfiguration.getProviders())
        .map(ProvidersConfiguration::getOidc)
        .ifPresent(providers::putAll);

    return providers;
  }

  public OidcAuthenticationConfiguration getOidcAuthenticationConfigurationById(
      final String registrationId) {
    return providers.get(registrationId);
  }

  public Map<String, OidcAuthenticationConfiguration> getOidcAuthenticationConfigurations() {
    return providers;
  }
}
