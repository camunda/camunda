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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * Opt-in configuration that adopts CSL for Optimize. See <a
 * href="https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md">ADR-0038</a>.
 *
 * <p>Activated by {@code optimize.security.csl.enabled=true}. The legacy adapters ({@code
 * CCSMSecurityConfigurerAdapter} / {@code CCSaaSSecurityConfigurerAdapter}) carry the inverse
 * condition, so exactly one security setup is active at a time.
 *
 * <p>Because CSL gives the API and webapp chains distinct orders (API before webapp), Optimize can
 * use the umbrella {@code CamundaSecurityAutoConfiguration} directly and return {@code /**} from
 * {@link SecurityPathPort#webappPaths()}: the stock webapp chain becomes the catch-all that sorts
 * below the bearer API chain. No custom webapp chain bean is needed.
 *
 * <p>Required application config:
 *
 * <ul>
 *   <li>{@code optimize.security.csl.enabled=true}
 *   <li>{@code camunda.security.authentication.method=oidc}
 *   <li>{@code camunda.security.authentication.oidc.*} for the Identity (CCSM) / Auth0 (CCSaaS)
 *       client registration
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "optimize.security.csl.enabled", havingValue = "true")
@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)
public class OptimizeCamundaSecurityConfig {

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
