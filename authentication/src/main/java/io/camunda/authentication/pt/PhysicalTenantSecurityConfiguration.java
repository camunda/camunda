/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static io.camunda.spring.utils.PhysicalTenantContext.DEFAULT_PHYSICAL_TENANT_ID;

import io.camunda.security.api.context.CamundaSecurityScopeProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
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

  /**
   * Unifies the unprefixed cluster ({@code /v2}) security chain with the {@code default} physical
   * tenant, so the two surfaces for the default tenant are identical.
   *
   * <p>CSL builds the cluster chain from {@link
   * CamundaSecurityLibraryProperties#getAuthentication()} (raw root {@code
   * camunda.security.authentication.*}), while {@link PhysicalTenantScopeProvider} builds the
   * {@code /physical-tenants/default} alias from {@link
   * PhysicalTenantAuthConfigurations#forPhysicalTenant} (root + the {@code
   * camunda.physical-tenants.default.*} overlay, narrowed to {@code providers.assigned}). When any
   * physical tenant is configured, this {@link BeanPostProcessor} replaces the cluster
   * authentication with the default tenant's resolved config <em>before</em> CSL builds its chains
   * — so {@code /v2} and {@code /physical-tenants/default} carry the same providers, and {@code
   * camunda.physical-tenants.default.providers.assigned} limits the cluster surface too. CSL stays
   * physical-tenant-agnostic: this only mutates OC-owned config it already consumes.
   *
   * <p>Declared {@code static} for the same reason as {@link #physicalTenantScopeProvider} — a
   * {@code BeanPostProcessor} must be instantiated before the beans it post-processes.
   */
  @Bean
  public static BeanPostProcessor physicalTenantClusterAuthUnification(
      final Environment environment) {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof final CamundaSecurityLibraryProperties props
            && PhysicalTenantScopeProvider.hasConfiguredPhysicalTenants(environment)) {
          props.setAuthentication(
              PhysicalTenantAuthConfigurations.forPhysicalTenant(
                  DEFAULT_PHYSICAL_TENANT_ID, environment));
        }
        return bean;
      }
    };
  }
}
