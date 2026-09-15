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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Shared wiring for {@link OptimizeCloudSecurityConfiguration} (CCSaaS) and {@link
 * OptimizeCcsmSecurityConfiguration} (CCSM): both override CSL's default {@link
 * TokenValidatorFactory} to append edition-specific validators. {@link #tokenValidatorFactory} is
 * used by both editions and gates the bearer/API {@code JwtDecoder} plus the session's per-request
 * access token decode. {@link OptimizeCloudSecurityConfiguration} additionally reuses the same
 * factory to gate the login id_token too — CCSaaS does so because its org/cluster validators are
 * lenient on claim absence, but CCSM does <em>not</em>: see {@link
 * OptimizeIdentityPermissionValidator}'s javadoc for why routing the id_token through an
 * audience-strict Identity check would reject every real login. That id_token wiring has exactly
 * one caller, so it is inlined directly in {@link OptimizeCloudSecurityConfiguration} rather than
 * shared here.
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
}
