/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.adapter;

import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spi.OidcConfigurationProvider;
import io.camunda.gatekeeper.spring.condition.ConditionalOnAuthenticationMethod;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
public final class OidcConfigurationAdapter implements OidcConfigurationProvider {

  private static final String DEFAULT_REGISTRATION_ID = "oidc";

  private final Map<String, OidcConfig> configs;

  public OidcConfigurationAdapter(final SecurityConfiguration securityConfiguration) {
    configs = initializeConfigs(securityConfiguration);
  }

  @Override
  public List<OidcConfig> getConfigurations() {
    return List.copyOf(configs.values());
  }

  @Override
  public Optional<OidcConfig> getConfiguration(final String registrationId) {
    return Optional.ofNullable(configs.get(registrationId));
  }

  private Map<String, OidcConfig> initializeConfigs(
      final SecurityConfiguration securityConfiguration) {
    final var result = new HashMap<String, OidcConfig>();
    final var authConfig = securityConfiguration.getAuthentication();

    Optional.ofNullable(authConfig.getOidc())
        .filter(c -> Objects.nonNull(c.getClientId()) && !c.getClientId().isBlank())
        .ifPresent(
            c -> result.put(DEFAULT_REGISTRATION_ID, toOidcConfig(c, DEFAULT_REGISTRATION_ID)));

    return result;
  }

  private OidcConfig toOidcConfig(
      final OidcAuthenticationConfiguration config, final String registrationId) {
    final var scope = config.getScope() != null ? String.join(" ", config.getScope()) : null;
    final var audiences =
        config.getAudiences() != null ? new ArrayList<>(config.getAudiences()) : List.<String>of();

    return new OidcConfig(
        config.getIssuerUri(),
        config.getClientId(),
        config.getClientSecret(),
        config.getJwkSetUri(),
        config.getAdditionalJwkSetUris(),
        config.getAuthorizationUri(),
        config.getTokenUri(),
        config.getEndSessionEndpointUri(),
        config.getUsernameClaim(),
        config.getClientIdClaim(),
        config.getGroupsClaim(),
        config.isPreferUsernameClaim(),
        scope,
        audiences,
        config.getRedirectUri(),
        config.getClockSkew(),
        config.isIdpLogoutEnabled(),
        config.getGrantType(),
        config.getClientAuthenticationMethod(),
        registrationId,
        null);
  }
}
