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
import io.camunda.security.core.port.out.SessionStorePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * SPIKE (ADR-0036): single opt-in configuration that adopts CSL for Optimize. Gate it as needed
 * (property/profile); during prototyping it coexists with the legacy security config so both can be
 * compared.
 *
 * <h2>Why individual imports instead of the umbrella</h2>
 *
 * <p>OC activates CSL via {@code @ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)}.
 * That umbrella includes {@code OidcWebappSecurityConfiguration}, whose chain runs at {@code
 * ORDER_WEBAPP_API = 1} — the same order as the bearer API chain. Optimize's webapp chain is a
 * {@code /**} catch-all and MUST sort below the API chain (see {@link
 * OptimizeWebappSecurityConfiguration}). So Optimize cannot take the stock webapp chain from the
 * umbrella; it imports the CSL pieces it needs and supplies its own catch-all webapp chain.
 *
 * <p>SPIKE NOTE: the import list below is the best-effort subset of the umbrella minus {@code
 * OidcWebappSecurityConfiguration} and the scoped/basic-auth/admin pieces Optimize does not use. It
 * must be verified against {@code CamundaSecurityAutoConfiguration}. The cleaner long-term fix is a
 * CSL change that makes the webapp chain order configurable (or a catch-all webapp variant), after
 * which Optimize could use the umbrella directly. See SPIKE-NOTES.md.
 *
 * <p>Required application config for this to hold together:
 *
 * <ul>
 *   <li>{@code camunda.security.authentication.method=oidc}
 *   <li>{@code camunda.security.unhandled-paths-chain.enabled=false} (so the CSL deny chain does not
 *       collide with the {@code /**} webapp chain)
 *   <li>{@code camunda.security.authentication.oidc.*} for the Identity (CCSM) / Auth0 (CCSaaS)
 *       client registration
 *   <li>{@code camunda.security.session.persistent.enabled=true} once the session store adapter is
 *       implemented
 * </ul>
 */
@Configuration
@Import(OptimizeWebappSecurityConfiguration.class)
// TODO(spike): replace this hand-picked list with the umbrella once CSL makes the webapp chain
// order configurable. Verify each class name against io.camunda.security.spring
// .CamundaSecurityAutoConfiguration. Excludes OidcWebappSecurityConfiguration by design.
@org.springframework.boot.autoconfigure.ImportAutoConfiguration({
  io.camunda.security.spring.CamundaSecurityConfiguration.class,
  io.camunda.security.spring.security.BaseSecurityConfiguration.class,
  io.camunda.security.spring.security.OidcApiSecurityConfiguration.class,
  io.camunda.security.spring.oidc.OidcBeansConfiguration.class,
  io.camunda.security.spring.oidc.OidcClaimsProviderConfiguration.class,
  io.camunda.security.spring.oidc.ScopedOidcInfrastructureConfiguration.class,
  io.camunda.security.spring.context.CamundaAuthenticationBeansConfiguration.class,
  io.camunda.security.spring.authz.AuthorizationCheckerConfiguration.class,
  io.camunda.security.spring.authz.AuthorizationConfiguration.class,
  io.camunda.security.spring.handler.AuthFailureHandlerConfiguration.class,
  io.camunda.security.spring.security.WebAppAuthorizationFilterConfiguration.class,
  io.camunda.security.spring.user.UserConfiguration.class,
})
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

  @Bean
  @ConditionalOnMissingBean
  public SessionStorePort sessionStorePort() {
    return new OptimizeSessionStoreAdapter();
  }
}
