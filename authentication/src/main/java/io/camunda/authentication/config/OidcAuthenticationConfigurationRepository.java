/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.api.model.config.oidc.OidcProvidersConfiguration;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class OidcAuthenticationConfigurationRepository implements OidcProviderConfigurationPort {

  public static final String REGISTRATION_ID = "oidc";
  private final Map<String, OidcConfiguration> providers;

  public OidcAuthenticationConfigurationRepository(
      final CamundaSecurityLibraryProperties cslProperties) {
    providers = initializeProviders(cslProperties);
  }

  protected Map<String, OidcConfiguration> initializeProviders(
      final CamundaSecurityLibraryProperties cslProperties) {
    final var providers = new HashMap<String, OidcConfiguration>();
    final var authenticationConfiguration = cslProperties.getAuthentication();

    Optional.ofNullable(authenticationConfiguration.getOidc())
        .filter(c -> Objects.nonNull(c.getClientId()) && !c.getClientId().isBlank())
        .ifPresent(c -> providers.put(REGISTRATION_ID, c));

    Optional.ofNullable(authenticationConfiguration.getProviders())
        .map(OidcProvidersConfiguration::getOidc)
        .ifPresent(providers::putAll);

    return providers;
  }

  public OidcConfiguration getOidcAuthenticationConfigurationById(final String registrationId) {
    return providers.get(registrationId);
  }

  public Map<String, OidcConfiguration> getOidcAuthenticationConfigurations() {
    return providers;
  }
}
