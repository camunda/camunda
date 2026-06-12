/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the physical-tenant security SPI into the OC application context.
 *
 * <p>The {@link PhysicalTenantScopeProvider} bean is declared as a {@code static @Bean} (a CSL
 * adopter requirement) so it is instantiated during the early BeanDefinitionRegistryPostProcessor
 * phase — before any regular configuration-class post-processing — giving CSL's scoped-chain
 * builder access to the descriptor list before the security filter chains are built.
 *
 * <p>The request-scoped tenant id those chains rely on is stamped by {@code PhysicalTenantFilter}
 * (gateway-rest), registered before Spring Security — see {@code ApiFiltersConfiguration}.
 */
@Configuration
public class PhysicalTenantSecurityConfiguration {

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
}
