/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.SecurityConfiguration;
import java.util.LinkedList;
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
 *   <li>{@link OidcAuthenticationConfigurationRepository} – for client-specific validation settings
 *   <li>{@link SecurityConfiguration} – for global security settings such as SaaS
 *       organization/cluster
 * </ul>
 *
 * <p>The resulting validator may include:
 *
 * <ul>
 *   <li>{@link AudienceValidator} – if the client is configured with expected audiences
 *   <li>{@link OrganizationValidator} – if SaaS configuration is present
 *   <li>{@link ClusterValidator} – if SaaS configuration is present
 * </ul>
 *
 * <p>If no additional validators are required, a {@link DelegatingOAuth2TokenValidator} with
 * timestamp validation is used. Note: Type validation (JWT/at+jwt) is handled separately by the
 * {@link OidcAccessTokenDecoderFactory}.
 */
public class TokenValidatorFactory {

  private final SecurityConfiguration securityConfiguration;
  private final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository;

  public TokenValidatorFactory(
      final SecurityConfiguration securityConfiguration,
      final OidcAuthenticationConfigurationRepository oidcAuthenticationConfigurationRepository) {
    this.securityConfiguration = securityConfiguration;
    this.oidcAuthenticationConfigurationRepository = oidcAuthenticationConfigurationRepository;
  }

  /**
   * Creates a new {@link OAuth2TokenValidator} for the given {@link ClientRegistration}. The
   * resulting validator may include one or more of:
   *
   * <ul>
   *   <li>{@link AudienceValidator} – if audience validation is configured for the client
   *   <li>{@link OrganizationValidator} and {@link ClusterValidator} – if SaaS configuration is
   *       present
   * </ul>
   *
   * @param clientRegistration the client registration associated with the JWT issuer
   * @return a composed {@code OAuth2TokenValidator} instance
   */
  public OAuth2TokenValidator<Jwt> createTokenValidator(
      final ClientRegistration clientRegistration) {
    final var registrationId = clientRegistration.getRegistrationId();
    final var oidcAuthenticationConfiguration =
        oidcAuthenticationConfigurationRepository.getOidcAuthenticationConfigurationById(
            registrationId);
    final var validators = new LinkedList<OAuth2TokenValidator<Jwt>>();

    validators.add(
        new JwtTimestampValidator(
            securityConfiguration.getAuthentication().getOidc().getClockSkew()));

    final var validAudiences = oidcAuthenticationConfiguration.getAudiences();
    if (validAudiences != null) {
      validators.add(new AudienceValidator(validAudiences));
    }

    if (securityConfiguration.getSaas().isConfigured()) {
      validators.add(
          new OrganizationValidator(securityConfiguration.getSaas().getOrganizationId()));
      validators.add(new ClusterValidator(securityConfiguration.getSaas().getClusterId()));
    }

    return new DelegatingOAuth2TokenValidator<>(validators);
  }
}
