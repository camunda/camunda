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
 * CCSM security wiring under CSL, active on the self-managed profile unless {@code
 * optimize.security.csl.enabled=false}. Enforces the Identity {@code write:*} (OPTIMIZE_PERMISSION)
 * gate through CSL's host extension points, which CSL's default {@link TokenValidatorFactory} does
 * not cover: it only checks issuer, signature and expiry, so any principal the IdP authenticates
 * would reach Optimize.
 *
 * <p>The gate applies to bearer tokens through {@link #tokenValidatorFactory} and to session logins
 * through {@link OptimizeCcsmSessionPermissionEnforcementFilter}, because CSL swallows a validation
 * failure on the session path and falls back to the id_token's claims.
 *
 * <p>The login id_token itself is exempt: unlike {@link OptimizeCloudSecurityConfiguration}
 * (CCSaaS) this configuration does not override {@code idTokenDecoderFactory}, which leaves Spring
 * Security's stock decoder in place. {@link OptimizeIdentityPermissionValidator} requires a {@code
 * write:*} permission claim the id_token does not carry, and rejects its {@code aud} on top of that
 * whenever {@code camunda.identity.audience} is configured, because the id_token is audienced to
 * the OIDC client-id instead. Routing it through the validator would therefore reject every
 * interactive login.
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
   * Token validation for bearer/API tokens: CSL's checks plus the Identity permission gate.
   * Overrides CSL's {@code @ConditionalOnMissingBean} default.
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
   * Installs {@link OptimizeCcsmSessionPermissionEnforcementFilter} into CSL's chains. {@link
   * SecurityHeadersCustomizer} is repurposed as the carrier because it is the only extension point
   * CSL exposes that receives the real {@link HttpSecurity} builder, and every chain builder
   * applies it regardless of its "headers" name.
   *
   * <p>The filter therefore also lands on CSL's unprotected-paths chain ({@code
   * BaseSecurityConfiguration#unprotectedPathsSecurityFilterChain}), where it is a no-op: that
   * chain installs no {@code SessionRepositoryFilter} and CSL registers the default one with {@code
   * registration.setEnabled(false)}, so nothing resolves the {@code SESSION} cookie there and no
   * {@code SecurityContext} is restored.
   *
   * <p>Anchored after {@link AuthorizationFilter}, which CSL's {@code OAuth2RefreshTokenFilter}
   * shares on the webapp chain and claims first, so an expired access token is refreshed before it
   * is verified. CSL's API chain installs no refresh filter, which is why the filter skips an
   * expired token instead of denying it (see its javadoc).
   */
  @Bean
  public SecurityHeadersCustomizer ccsmSessionPermissionEnforcementFilterInstaller(
      final CCSMTokenService ccsmTokenService) {
    final OptimizeCcsmSessionPermissionEnforcementFilter filter =
        new OptimizeCcsmSessionPermissionEnforcementFilter(ccsmTokenService);
    return httpSecurity -> httpSecurity.addFilterAfter(filter, AuthorizationFilter.class);
  }

  /**
   * Chain for {@code /api/public/**} and {@code /api/ingestion/variable}, the client-credentials
   * surface that is audience-checked but not gated through Identity. {@link
   * OptimizeSecurityPathAdapter#apiPaths()} places both paths in the shared API chain, where {@link
   * OptimizeIdentityPermissionValidator} would reject any token without a {@code write:*} Identity
   * grant. Ordered ahead of CSL's {@code oidcApiSecurityFilterChain} ({@code
   * CamundaSecurityFilterChainConstants#ORDER_API}) so it claims both paths first; sharing {@code
   * ORDER_UNPROTECTED} with CSL's public chain is safe because their path patterns never overlap.
   *
   * <p>Its {@link JwtDecoder} applies CSL's own issuer, signature, expiry and audience checks
   * without the Identity gate, plus {@code api.audience}. CSL validates against one merged audience
   * set that {@code OptimizeSecurityConfigCompatibilityPostProcessor} bridges both the Identity and
   * the public API audience into, and accepts a token matching any entry of it, so without pinning
   * {@code api.audience} an Identity-audience token would pass here and no Identity gate would
   * catch it. An unset {@code api.audience} leaves the merged set as the only audience check.
   *
   * <p>Assembled by CSL's {@link ScopedApiSecurityChainBuilder}, the same builder its own API chain
   * uses, so the operator's CORS source, HTTPS-redirect customizers, CSRF configuration, secure
   * headers and API authentication entry point keep applying. No {@code SessionRepositoryFilter} is
   * passed, this surface stays session-less, which also keeps CSL's CSRF protection inert because
   * its request matcher only demands a token once a session exists. {@link
   * OptimizeCcsmSessionPermissionEnforcementFilter} lands on this chain too and is a no-op, the
   * context holds a bearer {@code JwtAuthenticationToken} and never an {@code
   * OAuth2AuthenticationToken}.
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
    final TokenValidatorFactory validatorFactoryWithoutIdentityGate =
        OptimizeTokenValidatorFactorySupport.tokenValidatorFactory(
            oidcProviderConfigurationPort,
            cslProperties,
            publicApiAudienceValidator(configurationService));
    final JwtDecoder decoder =
        oidcAccessTokenDecoderFactory.selectAccessTokenDecoder(
            allClientRegistrations(clientRegistrationRepository),
            oidcProviderConfigurationPort.getOidcAuthenticationConfigurations(),
            validatorFactoryWithoutIdentityGate);

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
