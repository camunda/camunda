/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.spi.OidcConfigurationProvider;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for OIDC provider configurations. Combines the primary config from {@link
 * AuthenticationConfig} with additional dynamic configurations from {@link
 * OidcConfigurationProvider}.
 */
public class OidcAuthenticationConfigurationRepository {

  public static final String REGISTRATION_ID = "oidc";
  private final Map<String, OidcConfig> providers;

  public OidcAuthenticationConfigurationRepository(
      final AuthenticationConfig authenticationConfig) {
    this(authenticationConfig, null);
  }

  public OidcAuthenticationConfigurationRepository(
      final AuthenticationConfig authenticationConfig,
      final OidcConfigurationProvider oidcConfigurationProvider) {
    providers = initializeProviders(authenticationConfig, oidcConfigurationProvider);
  }

  protected Map<String, OidcConfig> initializeProviders(
      final AuthenticationConfig authenticationConfig,
      final OidcConfigurationProvider oidcConfigurationProvider) {
    final var result = new HashMap<String, OidcConfig>();

    final var oidcConfig = authenticationConfig.oidc();
    if (oidcConfig != null && oidcConfig.clientId() != null && !oidcConfig.clientId().isBlank()) {
      result.put(REGISTRATION_ID, oidcConfig);
    }

    if (oidcConfigurationProvider != null) {
      for (final var config : oidcConfigurationProvider.getConfigurations()) {
        final var registrationId = config.registrationId();
        if (registrationId != null && !registrationId.isBlank()) {
          result.put(registrationId, config);
        }
      }
    }

    return result;
  }

  /**
   * Returns the OIDC configuration for the given registration ID.
   *
   * @param registrationId the registration ID
   * @return the OIDC configuration, or {@code null} if not found
   */
  public OidcConfig getOidcConfigById(final String registrationId) {
    return providers.get(registrationId);
  }

  /**
   * Returns all registered OIDC configurations.
   *
   * @return an unmodifiable view of all OIDC configurations keyed by registration ID
   */
  public Map<String, OidcConfig> getOidcConfigurations() {
    return Map.copyOf(providers);
  }
}
