/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.optimize.tomcat.CCSMRequestAdjustmentFilter;
import io.camunda.security.api.context.CamundaAuthenticationConverter;
import io.camunda.security.api.context.OidcClaimsProvider;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import io.camunda.security.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.security.spring.converter.OidcUserAuthenticationConverter;
import io.camunda.security.spring.oidc.OidcAccessTokenDecoderFactory;
import io.camunda.security.spring.session.WebSessionConfiguration;
import io.camunda.security.spring.spi.OidcAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Default configuration that adopts CSL for Optimize. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Active whenever {@code optimize.security.csl.enabled} is {@code true} or absent — the default
 * since 8.10 (camunda/camunda#58483). An operator can opt back into the legacy stack with {@code
 * optimize.security.csl.enabled=false} until the flag and the legacy adapters ({@code
 * CCSMSecurityConfigurerAdapter} / {@code CCSaaSSecurityConfigurerAdapter}) are removed together at
 * 8.11 (camunda/camunda#58484). The two carry the inverse condition, so exactly one security setup
 * is active at a time.
 *
 * <p>Because CSL gives the API and webapp chains distinct orders (API before webapp), Optimize can
 * use the umbrella {@code CamundaSecurityAutoConfiguration} directly and return {@code /**} from
 * {@link SecurityPathPort#webappPaths()}: the stock webapp chain becomes the catch-all that sorts
 * below the bearer API chain. No custom webapp chain bean is needed.
 *
 * <p>{@link WebSessionConfiguration} is imported explicitly because the umbrella does not include
 * it: it carries the session lifecycle beans (repository, mapper, attribute converter, expiry
 * sweep) that persist through {@link OptimizeSessionStoreAdapter}. It self-activates on {@code
 * camunda.security.session.persistent.enabled}, so without that property CSL keeps its in-memory
 * sessions and nothing here changes.
 *
 * <p>Required application config (with {@code optimize.security.csl.enabled} left at its default of
 * {@code true}):
 *
 * <ul>
 *   <li>{@code camunda.security.authentication.method=oidc}
 *   <li>{@code camunda.security.authentication.oidc.*} for the Identity (CCSM) / Auth0 (CCSaaS)
 *       client registration
 * </ul>
 */
@Configuration
@ConditionalOnProperty(
    name = "optimize.security.csl.enabled",
    havingValue = "true",
    matchIfMissing = true)
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
@Import(WebSessionConfiguration.class)
public class OptimizeCamundaSecurityConfig {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeCamundaSecurityConfig.class);

  @Bean
  public SecurityPathPort securityPathPort() {
    return new OptimizeSecurityPathAdapter();
  }

  /**
   * Stub membership port CSL's claim converters depend on; see {@link OptimizeMembershipAdapter}.
   */
  @Bean
  public MembershipPort membershipPort() {
    return new OptimizeMembershipAdapter();
  }

  /**
   * Keeps external sharing working under CSL. The legacy adapters each register their own
   * request-adjustment filter, and both back off in CSL mode, so without this bean the {@code
   * /external/api/**} rewrite and the static share resources stop resolving.
   *
   * <p>{@link CCSMRequestAdjustmentFilter} serves both editions here despite its name: all it does
   * is strip the servlet context path, rewrite {@code /external/api/**} to {@code /api/external/**}
   * and serve the static share resources. The CCSaaS-only cluster-id stripping its SaaS counterpart
   * adds is not needed, because in CSL mode the cluster id is the servlet context path (see <a
   * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>).
   *
   * <p>Registered at highest precedence so the rewrite lands before the Spring Security filter
   * chain, which matches on the (wrapped) request URI.
   */
  @Bean
  public FilterRegistrationBean<CCSMRequestAdjustmentFilter> externalSharingRequestAdjuster() {
    LOG.debug("Registering filter 'externalSharingRequestAdjuster' (CSL)...");
    final FilterRegistrationBean<CCSMRequestAdjustmentFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(new CCSMRequestAdjustmentFilter());
    registration.addUrlPatterns("/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * Converts a CSL login session ({@code OAuth2AuthenticationToken}) into a {@code
   * CamundaAuthentication}. CSL ships the converter but registers no converter beans of its own, so
   * without this the delegating converter finds no match and every request that resolves the
   * current user fails with a 500.
   */
  @Bean
  public CamundaAuthenticationConverter<Authentication> oidcUserAuthenticationConverter(
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OidcAccessTokenDecoderFactory accessTokenDecoderFactory,
      final LazyTokenClaimsConverter tokenClaimsConverter,
      final HttpServletRequest request) {
    return new OidcUserAuthenticationConverter(
        authorizedClientRepository, accessTokenDecoderFactory, tokenClaimsConverter, request);
  }

  /**
   * The same for bearer tokens ({@code JwtAuthenticationToken}) on the API chain, so the public API
   * does not hit the identical failure. Optimize needs both because one {@code
   * CamundaAuthenticationProvider} serves both chains.
   */
  @Bean
  public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
      final LazyTokenClaimsConverter tokenClaimsConverter,
      final OidcClaimsProvider oidcClaimsProvider) {
    return new OidcTokenAuthenticationConverter(tokenClaimsConverter, oidcClaimsProvider);
  }

  /** Overrides CSL's default OIDC entry point; see {@link OptimizeOidcAuthenticationEntryPoint}. */
  @Bean
  public OidcAuthenticationEntryPoint oidcAuthenticationEntryPoint(
      final ClientRegistrationRepository clientRegistrationRepository) {
    return new OptimizeOidcAuthenticationEntryPoint(
        resolveLoginRedirectTarget(clientRegistrationRepository));
  }

  // Mirrors CSL's redirect resolution: a single registered client redirects straight to its
  // /oauth2/authorization/{id} endpoint; anything else falls back to /login so a picker can show.
  private static String resolveLoginRedirectTarget(
      final ClientRegistrationRepository clientRegistrationRepository) {
    if (clientRegistrationRepository instanceof final Iterable<?> registrations) {
      ClientRegistration single = null;
      int count = 0;
      for (final Object registration : registrations) {
        if (registration instanceof final ClientRegistration clientRegistration) {
          single = clientRegistration;
          count++;
        }
      }
      if (count == 1) {
        return "/oauth2/authorization/" + single.getRegistrationId();
      }
    }
    return "/login";
  }
}
