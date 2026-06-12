/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the physical-tenant security SPI beans into the OC application context.
 *
 * <p>The {@link PhysicalTenantScopeProvider} bean is declared as a {@code static @Bean} (a CSL
 * adopter requirement) so it is instantiated during the early BeanDefinitionRegistryPostProcessor
 * phase — before any regular configuration-class post-processing — giving CSL's scoped-chain
 * builder access to the descriptor list before the security filter chains are built.
 *
 * <p>The {@link PhysicalTenantPreSecurityFilter} is registered at an order lower than Spring
 * Security's {@code springSecurityFilterChain} (order {@code -100}) so that the physical tenant id
 * is available on the request when components that consume it run inside the security chain
 * (per-tenant user resolution, per-tenant OIDC, webapp session storage).
 */
@Configuration
public class PhysicalTenantSecurityConfiguration {

  // Spring Security's FilterChainProxy registers at SecurityProperties.DEFAULT_FILTER_ORDER (-100);
  // our pre-security filter must run just before it. We inline the value rather than reference
  // org.springframework.boot.security.autoconfigure.SecurityProperties (a) to avoid a dependency on
  // spring-boot-security solely for this, and (b) because DEFAULT_FILTER_ORDER is a compile-time
  // constant — referencing it inlines the value anyway, leaving the dependency flagged as unused by
  // dependency:analyze.
  private static final int SPRING_SECURITY_FILTER_CHAIN_ORDER = -100;

  /**
   * Produces the {@link CamundaSecurityScopeProvider} that emits one {@link
   * io.camunda.security.api.model.config.ScopedSecurityDescriptor} per explicitly configured
   * physical tenant. CSL's scoped-chain builder consumes this provider when building the per-tenant
   * API {@code SecurityFilterChain}s.
   *
   * <p>Must be {@code static} so Spring instantiates it before configuration-class post-processing;
   * without {@code static}, CSL's {@code @ConditionalOnBean(ScopeProvider.class)} conditions on
   * other beans evaluate against an incomplete bean graph and the scoped chains are not built
   * (camunda/camunda-security-library adopter requirement).
   */
  @Bean
  public static CamundaSecurityScopeProvider physicalTenantScopeProvider(
      final Environment environment) {
    return new PhysicalTenantScopeProvider(environment);
  }

  /**
   * Registers the {@link PhysicalTenantPreSecurityFilter} before Spring Security's filter chain.
   * This ensures the physical tenant id extracted from {@code /physical-tenants/{id}/...} paths is
   * available to the components that consume it inside the security filter chain (which runs after
   * {@code FilterRegistrationBean} filters at lower order values).
   *
   * <p>The filter is permissive and does not validate the id against the configured tenants:
   * unknown tenants are rejected by CSL's catch-all security chain with 404. See ADR-0003.
   */
  @Bean
  public FilterRegistrationBean<PhysicalTenantPreSecurityFilter>
      physicalTenantPreSecurityFilterRegistration() {
    final var registration = new FilterRegistrationBean<>(new PhysicalTenantPreSecurityFilter());
    // Run before Spring Security's FilterChainProxy so the PT id is stamped on the request
    // before any security filter chain inspects it.
    registration.setOrder(SPRING_SECURITY_FILTER_CHAIN_ORDER - 1);
    registration.addUrlPatterns("/physical-tenants/*");
    return registration;
  }
}
