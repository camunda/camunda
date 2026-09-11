/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * CCSM security wiring for the CSL adoption, active under the self-managed profile whenever CSL is
 * active — the default since 8.10 (camunda/camunda#58483), or opted out of with {@code
 * optimize.security.csl.enabled=false} through 8.10. Restores the Identity {@code write:*}
 * (OPTIMIZE_PERMISSION) gate the legacy {@code CCSMSecurityConfigurerAdapter} / {@code
 * CCSMAuthenticationCookieFilter} enforced, using CSL's host extension points. Mirrors {@link
 * OptimizeCloudSecurityConfiguration}'s approach for CCSaaS.
 *
 * <p>Without this configuration, CSL's default {@link TokenValidatorFactory} only checks
 * issuer/signature/expiry: any principal the configured IdP authenticates would reach Optimize,
 * regardless of whether Identity ever granted it the Optimize permission.
 *
 * <p>One shared {@link TokenValidatorFactory} carries the {@link
 * OptimizeIdentityPermissionValidator}, and {@code idTokenDecoderFactory} reuses it, so the gate
 * applies to both the interactive login id_token and bearer/public-API tokens.
 */
@Configuration
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCcsmSecurityConfiguration {

  /**
   * Shared token validation for the login id_token and bearer/public-API tokens. Overrides CSL's
   * {@code @ConditionalOnMissingBean} default to append the Identity permission gate. The gate is
   * always added: dropping it would silently reopen the CCSM authorization gap CSL introduced.
   */
  @Bean
  public TokenValidatorFactory tokenValidatorFactory(
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final CamundaSecurityLibraryProperties cslProperties,
      final CCSMTokenService ccsmTokenService) {
    final List<OAuth2TokenValidator<Jwt>> extraValidators =
        List.of(new OptimizeIdentityPermissionValidator(ccsmTokenService));
    return OptimizeTokenValidatorFactorySupport.tokenValidatorFactory(
        oidcProviderConfigurationPort, cslProperties, extraValidators);
  }

  /**
   * Interactive login id_token validation. Reuses the shared {@link #tokenValidatorFactory} so the
   * login token runs through the same Identity permission gate as bearer tokens, overriding CSL's
   * {@code @ConditionalOnMissingBean} default.
   */
  @Bean
  public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
      final TokenValidatorFactory tokenValidatorFactory) {
    return OptimizeTokenValidatorFactorySupport.idTokenDecoderFactory(tokenValidatorFactory);
  }
}
