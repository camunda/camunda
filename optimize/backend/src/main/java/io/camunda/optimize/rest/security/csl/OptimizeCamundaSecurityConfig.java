/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.SecurityPathPort;
import io.camunda.security.spring.CamundaSecurityAutoConfiguration;
import io.camunda.security.spring.spi.OidcAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * SPIKE (ADR-0036): opt-in configuration that adopts CSL for Optimize.
 *
 * <p>Activated by {@code optimize.security.csl.enabled=true}. The legacy adapters ({@code
 * CCSMSecurityConfigurerAdapter} / {@code CCSaaSSecurityConfigurerAdapter}) carry the inverse
 * condition, so exactly one security setup is active. This makes the spike manually testable by
 * flipping a single flag.
 *
 * <p>Because CSL now gives the API and webapp chains distinct orders (API before webapp, ADR-0036),
 * Optimize can use the umbrella {@code CamundaSecurityAutoConfiguration} directly and return {@code
 * /**} from {@link SecurityPathPort#webappPaths()}: the stock webapp chain becomes the catch-all
 * that sorts below the bearer API chain. No custom webapp chain bean is needed.
 *
 * <p>Required application config (the compat bridge supplies most of these; see {@code
 * CONFIG-COMPAT.md}):
 *
 * <ul>
 *   <li>{@code optimize.security.csl.enabled=true}
 *   <li>{@code camunda.security.authentication.method=oidc}
 *   <li>{@code camunda.security.unhandled-paths-chain.enabled=false} (so the CSL deny chain does
 *       not collide with the {@code /**} webapp chain)
 *   <li>{@code camunda.security.authentication.oidc.*} for the Identity (CCSM) / Auth0 (CCSaaS)
 *       client registration
 * </ul>
 *
 * <p>Session storage: server-side sessions are persisted to Optimize's Elasticsearch via {@link
 * OptimizeSessionStoreAdapter} (the `SessionStorePort` bean). CSL's {@code WebSessionConfiguration}
 * is imported here and self-activates on {@code camunda.security.session.persistent.enabled=true}
 * (set by the compat bridge); it supplies the Spring Session repository / mapper /
 * attribute-converter defaults. An OpenSearch adapter is a follow-up (the store is ES-only in this
 * spike).
 */
@Configuration
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
@Import(io.camunda.security.spring.session.WebSessionConfiguration.class)
public class OptimizeCamundaSecurityConfig {

  @Bean
  @ConditionalOnMissingBean
  public SecurityPathPort securityPathPort() {
    return new OptimizeSecurityPathAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public MembershipPort membershipPort() {
    return new OptimizeMembershipAdapter();
  }

  /**
   * Overrides CSL's default OIDC entry point so unauthenticated {@code /api/**} XHR calls get a 401
   * (which the SPA turns into a login) instead of a 302 to the IdP that a browser {@code fetch}
   * cannot follow cross-origin. See {@link OptimizeOidcAuthenticationEntryPoint}.
   */
  @Bean
  @ConditionalOnMissingBean
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
