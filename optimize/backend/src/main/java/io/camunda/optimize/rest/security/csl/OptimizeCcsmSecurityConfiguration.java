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
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * CCSM security wiring for the CSL adoption, active under the self-managed profile whenever CSL is
 * active — the default since 8.10 (camunda/camunda#58483), or opted out of with {@code
 * optimize.security.csl.enabled=false} through 8.10. Restores the Identity {@code write:*}
 * (OPTIMIZE_PERMISSION) gate the legacy {@code CCSMSecurityConfigurerAdapter} / {@code
 * CCSMAuthenticationCookieFilter} enforced, using CSL's host extension points.
 *
 * <p>Without this configuration, CSL's default {@link TokenValidatorFactory} only checks
 * issuer/signature/expiry: any principal the configured IdP authenticates would reach Optimize,
 * regardless of whether Identity ever granted it the Optimize permission.
 *
 * <p>Unlike {@link OptimizeCloudSecurityConfiguration} (CCSaaS), this configuration does <b>not</b>
 * override {@code idTokenDecoderFactory}: {@link OptimizeIdentityPermissionValidator} performs a
 * full identity-sdk re-verification requiring the token's {@code aud} claim to match {@code
 * camunda.identity.audience} (the API resource audience, e.g. {@code optimize-api}), which is a
 * different value from the OIDC client-id the login id_token is audienced to (e.g. {@code
 * optimize}) — CSL's own audience validators and CCSaaS's org/cluster validators are lenient on
 * claim absence, but an audience mismatch here is not absence, it is a hard rejection. Routing the
 * id_token through this validator would reject every legitimate interactive login. The legacy CCSM
 * stack never validated an id_token either: {@code CCSMAuthenticationCookieFilter} only ever
 * checked the actual OAuth2 access token.
 *
 * <p>CSL defines no id_token decoder of its own (Spring Security's stock {@code
 * OidcIdTokenDecoderFactory} handles the login id_token unmodified), so simply not overriding
 * {@code idTokenDecoderFactory} here is sufficient to exempt it. Overriding only {@link
 * #tokenValidatorFactory} still gates both the bearer/API resource-server {@code JwtDecoder} and,
 * for interactive session users, the per-request decode of the session's stored access token (both
 * consume this bean automatically) — the two paths that carry a genuine access token.
 */
@Configuration
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCcsmSecurityConfiguration {

  /**
   * Shared token validation for bearer/public-API tokens and the session's per-request access
   * token. Overrides CSL's {@code @ConditionalOnMissingBean} default to append the Identity
   * permission gate. The gate is always added: dropping it would silently reopen the CCSM
   * authorization gap CSL introduced.
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
}
