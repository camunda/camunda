/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.oidc;

import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.spring.validator.AudienceValidator;
import java.util.LinkedList;
import java.util.Set;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/**
 * A factory for creating {@link OAuth2TokenValidator} instances for validating {@link Jwt} tokens.
 *
 * <p>This factory uses configuration data from:
 *
 * <ul>
 *   <li>{@link OidcAuthenticationConfigurationRepository} - for client-specific validation settings
 *   <li>{@link OidcConfig} - for OIDC-specific settings such as clock skew and audiences
 * </ul>
 *
 * <p>The resulting validator may include:
 *
 * <ul>
 *   <li>{@link AudienceValidator} - if the client is configured with expected audiences
 *   <li>{@link JwtTimestampValidator} - for timestamp validation with configurable clock skew
 * </ul>
 */
public final class TokenValidatorFactory {

  private final OidcConfig primaryOidcConfig;
  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;

  public TokenValidatorFactory(
      final OidcConfig primaryOidcConfig,
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    this.primaryOidcConfig = primaryOidcConfig;
    this.oidcConfigRepository = oidcConfigRepository;
  }

  /**
   * Creates a new {@link OAuth2TokenValidator} for the given {@link ClientRegistration}. The
   * resulting validator may include one or more of:
   *
   * <ul>
   *   <li>{@link AudienceValidator} - if audience validation is configured for the client
   *   <li>{@link JwtTimestampValidator} - for timestamp validation
   * </ul>
   *
   * @param clientRegistration the client registration associated with the JWT issuer
   * @return a composed {@code OAuth2TokenValidator} instance
   */
  public OAuth2TokenValidator<Jwt> createTokenValidator(
      final ClientRegistration clientRegistration) {
    final var registrationId = clientRegistration.getRegistrationId();
    final var oidcConfig = oidcConfigRepository.getOidcConfigById(registrationId);

    final var validators = new LinkedList<OAuth2TokenValidator<Jwt>>();

    final var clockSkew =
        oidcConfig != null && oidcConfig.clockSkew() != null
            ? oidcConfig.clockSkew()
            : primaryOidcConfig.clockSkew();
    validators.add(new JwtTimestampValidator(clockSkew));

    final var audiences =
        oidcConfig != null ? oidcConfig.audiences() : primaryOidcConfig.audiences();
    if (audiences != null && !audiences.isEmpty()) {
      validators.add(new AudienceValidator(Set.copyOf(audiences)));
    }

    return new DelegatingOAuth2TokenValidator<>(validators);
  }
}
