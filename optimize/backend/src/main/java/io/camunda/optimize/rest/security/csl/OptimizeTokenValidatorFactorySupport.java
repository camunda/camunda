/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.util.List;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * Shared wiring for {@link OptimizeCloudSecurityConfiguration} (CCSaaS) and {@link
 * OptimizeCcsmSecurityConfiguration} (CCSM): both override CSL's default {@link
 * TokenValidatorFactory} to append edition-specific validators, and reuse the same factory to build
 * the login id_token decoder, so a single validator chain serves the interactive login id_token and
 * bearer/public-API tokens alike. This class carries the identical construction logic so a future
 * change to how {@link TokenValidatorFactory}/{@code idTokenDecoderFactory} must be wired only
 * needs applying once.
 */
final class OptimizeTokenValidatorFactorySupport {

  private OptimizeTokenValidatorFactorySupport() {}

  static TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final CamundaSecurityLibraryProperties cslProperties,
      final List<OAuth2TokenValidator<Jwt>> extraValidators) {
    return new TokenValidatorFactory(
        oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
        cslProperties.getAuthentication().getOidc().getClockSkew(),
        extraValidators);
  }

  static JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final TokenValidatorFactory tokenValidatorFactory) {
    final OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
    decoderFactory.setJwtValidatorFactory(tokenValidatorFactory::createTokenValidator);
    return decoderFactory;
  }
}
