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
 * {@code idTokenDecoderFactory} here is sufficient to exempt it. {@link #tokenValidatorFactory}
 * gates the bearer/API resource-server {@code JwtDecoder}, which consumes the bean automatically.
 * Interactive session users are not covered by it: CSL swallows the validation failure on that path
 * and falls back to the id_token's claims, which is why {@link
 * OptimizeCcsmSessionPermissionEnforcementFilter} enforces the permission for them instead.
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
   * Token validation for bearer/API tokens. Overrides CSL's {@code @ConditionalOnMissingBean}
   * default to append the Identity permission gate. The gate is always added: dropping it would
   * silently reopen the CCSM authorization gap CSL introduced.
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
   * Installs {@link OptimizeCcsmSessionPermissionEnforcementFilter} into CSL's
   * session-authenticated chains (webapp + API). CSL provides no dedicated "add an arbitrary filter
   * to every chain" SPI; {@link SecurityHeadersCustomizer} is the closest fit it does offer — a
   * plain {@code customize(HttpSecurity)} hook that {@code ScopedWebappSecurityChainBuilder},
   * {@code ScopedApiSecurityChainBuilder} and {@code UnprotectedApiSecurityConfiguration} all apply
   * while building their chain, regardless of its "headers" name. Repurposing it here is a
   * deliberate, documented deviation rather than a semantic fit: it is the only extension point CSL
   * exposes that receives the real {@link HttpSecurity} builder. Applying it to the unprotected
   * chain too is harmless: that chain never populates an {@code OAuth2AuthenticationToken}, so the
   * filter is a no-op there.
   *
   * <p>Anchored after {@link AuthorizationFilter}, which every CSL chain installs, and deliberately
   * not right after {@code SecurityContextHolderFilter}: CSL's {@code OAuth2RefreshTokenFilter}
   * shares this anchor on the webapp chain and is added before this customizer runs, so insertion
   * order (the tie-break among filters sharing an anchor) puts the refresh ahead of the check. An
   * expired access token is then renewed first and verified afterwards, the way the legacy {@code
   * CCSMAuthenticationCookieFilter} renewed before deciding. That ordering only holds on the webapp
   * chain, CSL's API chain has no refresh filter at all, which is why the filter skips an expired
   * token instead of denying it (see its javadoc).
   */
  @Bean
  public SecurityHeadersCustomizer ccsmSessionPermissionEnforcementFilterInstaller(
      final CCSMTokenService ccsmTokenService) {
    final OptimizeCcsmSessionPermissionEnforcementFilter filter =
        new OptimizeCcsmSessionPermissionEnforcementFilter(ccsmTokenService);
    return httpSecurity -> httpSecurity.addFilterAfter(filter, AuthorizationFilter.class);
  }

  /**
   * Carve-out chain for {@code /api/public/**} and {@code /api/ingestion/variable}: the
   * client-credentials/M2M surface that legacy CCSM ({@code
   * CCSMSecurityConfigurerAdapter#publicApiJwtDecoder}) only ever audience-checked, never gated
   * through Identity. {@link OptimizeSecurityPathAdapter#apiPaths()} places both paths in the same
   * shared chain as every interactive-user endpoint, so without this carve-out {@link
   * OptimizeIdentityPermissionValidator} would hard-reject any client-credentials token lacking a
   * {@code write:*} Identity grant — a regression from the legacy behaviour these two paths always
   * had. Ordered ahead of CSL's own {@code oidcApiSecurityFilterChain} ({@code
   * CamundaSecurityFilterChainConstants#ORDER_API}) so it claims both paths first; sharing {@code
   * ORDER_UNPROTECTED} with CSL's genuinely-public chain is safe because the two chains' path
   * patterns never overlap.
   *
   * <p>Builds its {@link JwtDecoder} the same way CSL's own default {@code jwtDecoder} bean does
   * ({@code OidcAccessTokenDecoderFactory#selectAccessTokenDecoder}) but with a freshly built
   * {@link TokenValidatorFactory} that carries none of the extra validators — i.e. the same
   * issuer/signature/expiry/audience checks CSL would otherwise apply, just without the Identity
   * gate {@link #tokenValidatorFactory} adds for every other path.
   *
   * <p>Instead it requires {@code api.audience}, the way the legacy decoder did. CSL validates
   * against one merged audience set ({@code OptimizeSecurityConfigCompatibilityPostProcessor}
   * bridges the Identity and public-API audiences into it) and its {@code AudienceValidator}
   * accepts a token matching any entry, so the shared set alone would let an Identity-audience
   * token through here, and this chain has no Identity gate to catch it. When {@code api.audience}
   * is not configured there is nothing to pin to and the merged set stays the only audience check.
   *
   * <p>The chain itself is assembled by CSL's {@link ScopedApiSecurityChainBuilder}, the same
   * builder its own API chain uses, so the operator's CORS source, HTTPS-redirect customizers, CSRF
   * configuration, secure headers and API authentication entry point keep applying to these two
   * paths. Hand-rolling the chain here silently dropped all of them for {@code /api/public/**} and
   * {@code /api/ingestion/variable}. No {@code SessionRepositoryFilter} is passed: this surface is
   * client-credentials only, so it stays session-less. Two consequences of going through the
   * builder: it also applies {@link SecurityHeadersCustomizer}, so {@link
   * OptimizeCcsmSessionPermissionEnforcementFilter} lands on this chain as well, where it is a
   * no-op because the context holds a bearer {@code JwtAuthenticationToken} and never an {@code
   * OAuth2AuthenticationToken}; and CSL's CSRF protection stays inert, because its request matcher
   * only demands a token once {@code request.getSession(false)} is non-null and this chain resolves
   * no session.
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
