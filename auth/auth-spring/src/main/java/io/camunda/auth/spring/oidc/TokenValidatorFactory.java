/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.spring.oidc;

import io.camunda.auth.spring.config.OidcAuthenticationConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

public class TokenValidatorFactory {

  private final OidcAuthenticationConfigurationRepository oidcConfigRepository;

  public TokenValidatorFactory(
      final OidcAuthenticationConfigurationRepository oidcConfigRepository) {
    this.oidcConfigRepository = oidcConfigRepository;
  }

  public OAuth2TokenValidator<Jwt> createTokenValidator(
      final ClientRegistration clientRegistration) {
    final OidcAuthenticationConfiguration config =
        oidcConfigRepository
            .getOidcAuthenticationConfigurations()
            .get(clientRegistration.getRegistrationId());

    final List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(
        new JwtTimestampValidator(
            config != null ? config.getClockSkew() : java.time.Duration.ofSeconds(60)));

    if (config != null && config.getAudiences() != null && !config.getAudiences().isEmpty()) {
      validators.add(new AudienceValidator(config.getAudiences()));
    }

    return new DelegatingOAuth2TokenValidator<>(validators);
  }
}
