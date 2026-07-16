/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.spring.security.ScopedWebappSecurityChainBuilder;
import io.camunda.security.spring.security.ScopedWebappSecurityChainBuilderConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SPIKE (ADR-0036): Optimize's webapp filter chain. It reuses the CSL {@link
 * ScopedWebappSecurityChainBuilder#buildOidcWebappChain} (the exact same session-based
 * {@code oauth2Login} chain OC uses) but registers it as a CATCH-ALL that sorts BELOW the bearer
 * API chain.
 *
 * <h2>Why not just reuse {@code OidcWebappSecurityConfiguration}?</h2>
 *
 * <p>CSL's stock {@code OidcWebappSecurityConfiguration} registers its chain at {@code
 * ORDER_WEBAPP_API = 1}, the SAME order as the bearer API chain ({@code OidcApiSecurityConfiguration}).
 * That is fine for OC because there the webapp matcher ({@code webappPaths()}) and the API matcher
 * ({@code apiPaths()}) are disjoint. Optimize's webapp matcher is {@code /**}, which OVERLAPS the API
 * paths. Two chains at the same order with overlapping matchers is order-undefined: the {@code /**}
 * webapp chain could shadow the bearer API chain and break {@code /api/public/**}.
 *
 * <p>So Optimize must place its catch-all webapp chain at a LOWER precedence (higher order value)
 * than the API chain. This configuration therefore imports the shared builder and registers the
 * chain itself at order {@link #ORDER_OPTIMIZE_WEBAPP_CATCH_ALL}, instead of importing the stock
 * {@code OidcWebappSecurityConfiguration}. This is a spike finding: a cleaner long-term fix is to
 * make the webapp chain's order configurable in CSL so a host can opt into "catch-all below API"
 * without re-declaring the bean.
 *
 * <p>The always-on CSL deny chain must be suppressed (see application config:
 * {@code camunda.security.unhandled-paths-chain.enabled=false}) so it does not collide with this
 * {@code /**} chain.
 */
@Configuration
@Import(ScopedWebappSecurityChainBuilderConfiguration.class)
public class OptimizeWebappSecurityConfiguration {

  /**
   * Below the bearer API chain ({@code ORDER_WEBAPP_API = 1}) so API paths are claimed first, and
   * above where the deny chain would sit (which Optimize disables entirely).
   */
  public static final int ORDER_OPTIMIZE_WEBAPP_CATCH_ALL = 2;

  @Bean
  @Order(ORDER_OPTIMIZE_WEBAPP_CATCH_ALL)
  public SecurityFilterChain optimizeWebappSecurityFilterChain(
      final HttpSecurity http,
      final ScopedWebappSecurityChainBuilder chainBuilder,
      final ClientRegistrationRepository clientRegistrationRepository,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final OAuth2AuthorizedClientManager authorizedClientManager)
      throws Exception {
    // The builder derives the /** matcher from SecurityPathPort.webappPaths() and assembles the
    // full oauth2Login + session + refresh chain exactly as OC's primary webapp chain.
    return chainBuilder.buildOidcWebappChain(
        http, clientRegistrationRepository, authorizedClientRepository, authorizedClientManager);
  }
}
