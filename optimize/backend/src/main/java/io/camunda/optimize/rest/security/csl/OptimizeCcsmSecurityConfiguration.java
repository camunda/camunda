/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import io.camunda.security.core.port.in.OidcProviderConfigurationPort;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.AudienceValidator;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.oidc.TokenValidatorFactory;
import io.camunda.security.spring.scope.ScopedApiSecurityChainBuilder;
import io.camunda.security.spring.security.CamundaSecurityFilterChainConstants;
import io.camunda.security.spring.security.SecurityHeadersCustomizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Adds to CSL what legacy CCSM did and CSL does not: the Identity {@code write:*}
 * (OPTIMIZE_PERMISSION) gate on session logins, and the {@code api.audience} pin on the public API
 * paths. Bearer tokens on the other {@code /api/**} paths stay ungated, as they were on legacy
 * CCSM.
 *
 * <p>{@code optimize.security.csl.enabled=false} selects the legacy stack instead.
 */
@Configuration
@Conditional(CCSMCondition.class)
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OptimizeCcsmSecurityConfiguration {

  private static final List<String> PUBLIC_API_CARVE_OUT_PATHS =
      List.of("/api/public/**", "/api/ingestion/variable");

  /**
   * {@link SecurityHeadersCustomizer} carries the filter because it is the only CSL extension point
   * that receives the real {@link HttpSecurity} builder, and every chain builder applies it. The
   * filter lands on all CSL chains, including the unprotected one, where it is inert for want of a
   * session.
   *
   * <p>It goes after {@link AuthorizationFilter} because CSL's {@code OAuth2RefreshTokenFilter}
   * anchors there too and claims the position first, so the webapp chain refreshes an expired token
   * before the filter reads it.
   */
  @Bean
  public SecurityHeadersCustomizer ccsmSessionPermissionEnforcementFilterInstaller(
      final CCSMTokenService ccsmTokenService) {
    final OptimizeCcsmSessionPermissionEnforcementFilter filter =
        new OptimizeCcsmSessionPermissionEnforcementFilter(ccsmTokenService);
    return httpSecurity -> httpSecurity.addFilterAfter(filter, AuthorizationFilter.class);
  }

  /**
   * Pins {@code api.audience} on the public API paths, which legacy CCSM did and the shared CSL API
   * chain does not. CSL validates one merged audience set and accepts a token that matches any
   * entry, so an Identity-audience token would otherwise reach the public API. An unset {@code
   * api.audience} leaves the merged set as the only audience check.
   *
   * <p>The order puts this chain ahead of CSL's {@code oidcApiSecurityFilterChain}, so it claims
   * both paths first. Sharing {@code ORDER_UNPROTECTED} with CSL's unprotected chain is safe
   * because the path patterns never overlap.
   *
   * <p>{@link ScopedApiSecurityChainBuilder} assembles it, so the operator's CORS, HTTPS-redirect,
   * CSRF and header settings keep applying. It gets no {@code SessionRepositoryFilter}: this
   * surface is session-less, which also keeps CSRF inert.
   */
  @Bean
  @Order(CamundaSecurityFilterChainConstants.ORDER_UNPROTECTED)
  public SecurityFilterChain optimizeCcsmPublicApiSecurityFilterChain(
      final HttpSecurity http,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OidcProviderConfigurationPort oidcProviderConfigurationPort,
      final OidcAccessTokenDecoderFactory oidcAccessTokenDecoderFactory,
      final CamundaSecurityLibraryProperties cslProperties,
      final ConfigurationService configurationService,
      final ScopedApiSecurityChainBuilder scopedApiSecurityChainBuilder)
      throws Exception {
    final TokenValidatorFactory validatorFactoryWithPublicApiAudience =
        OptimizeTokenValidatorFactorySupport.tokenValidatorFactory(
            oidcProviderConfigurationPort,
            cslProperties,
            publicApiAudienceValidator(configurationService));
    final JwtDecoder decoder =
        oidcAccessTokenDecoderFactory.selectAccessTokenDecoder(
            allClientRegistrations(clientRegistrationRepository),
            oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
            validatorFactoryWithPublicApiAudience);

    return scopedApiSecurityChainBuilder.buildOidcApiChain(
        http, PUBLIC_API_CARVE_OUT_PATHS, List.of(), decoder);
  }

  private static List<OAuth2TokenValidator<Jwt>> publicApiAudienceValidator(
      final ConfigurationService configurationService) {
    final String audience = configurationService.getOptimizeApiConfiguration().getAudience();
    if (audience == null || audience.isBlank()) {
      return List.of();
    }
    return List.of(new AudienceValidator(Set.of(audience)));
  }

  // Mirrors OptimizeCamundaSecurityConfig#resolveLoginRedirectTarget: ClientRegistrationRepository
  // exposes no standard "list all registrations" method, but CSL's implementation is Iterable.
  private static List<ClientRegistration> allClientRegistrations(
      final ClientRegistrationRepository repository) {
    final List<ClientRegistration> registrations = new ArrayList<>();
    if (repository instanceof final Iterable<?> iterable) {
      for (final Object candidate : iterable) {
        if (candidate instanceof final ClientRegistration registration) {
          registrations.add(registration);
        }
      }
    }
    return registrations;
  }
}
