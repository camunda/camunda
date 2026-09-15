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
 * Builds the {@link TokenValidatorFactory} that {@link OptimizeCloudSecurityConfiguration} (CCSaaS)
 * and {@link OptimizeCcsmSecurityConfiguration} (CCSM) use to override CSL's default with
 * edition-specific validators.
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
