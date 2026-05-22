/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import io.camunda.application.commons.identity.PhysicalTenantsConfiguredCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the two host-side pieces that make plain Spring MVC webapp controllers (Operate, Tasklist,
 * Admin) reachable under the {@code /physical-tenant/<id>/...} access path:
 *
 * <ol>
 *   <li>{@link PhysicalTenantWebappRequestMappingHandlerMapping} — registers PT-prefixed siblings
 *       of every webapp controller mapping so URLs like {@code
 *       /physical-tenant/<id>/operate/client-config.js} actually have a handler.
 *   <li>{@link PhysicalTenantWebappContextPathInterceptor} — rewrites the {@code contextPath} model
 *       attribute that index controllers emit, so the rendered SPA's base URL stays inside the PT
 *       prefix (otherwise the SPA bootstraps from the unprefixed {@code /operate/} and drops out of
 *       the PT cookie scope).
 * </ol>
 *
 * <p>Both pieces are conditional on {@code camunda.physical-tenants.*} being configured (same gate
 * as the rest of the PT wiring, see {@link PhysicalTenantsConfiguredCondition}).
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappMappingConfiguration implements WebMvcConfigurer {

  @Bean
  public PhysicalTenantWebappRequestMappingHandlerMapping
      physicalTenantWebappRequestMappingHandlerMapping() {
    final var mapping = new PhysicalTenantWebappRequestMappingHandlerMapping();
    mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return mapping;
  }

  @Bean
  public PhysicalTenantWebappContextPathInterceptor physicalTenantWebappContextPathInterceptor() {
    return new PhysicalTenantWebappContextPathInterceptor();
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry.addInterceptor(physicalTenantWebappContextPathInterceptor());
  }
}
