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

/**
 * Registers a secondary {@link PhysicalTenantWebappRequestMappingHandlerMapping} bean that Spring's
 * {@code DispatcherServlet} discovers alongside the primary {@code
 * PhysicalTenantRequestMappingHandlerMapping} (REST-scoped, in {@code zeebe/gateway-rest}). The two
 * cover disjoint URL spaces — REST {@code /v2/...} on the primary, webapp bases on the secondary —
 * so neither competes for the same path.
 *
 * <p>Order is set above Spring's default {@link RequestMappingHandlerMapping default RMHM} (which
 * runs at {@code Ordered.LOWEST_PRECEDENCE - 1} via {@code WebMvcConfigurationSupport}).
 * PT-prefixed patterns are strictly more specific than the unprefixed defaults, so running this
 * handler first cannot mis-route any non-PT URL.
 *
 * <p>Conditional on {@code camunda.physical-tenants.*} being configured — same gate as the rest of
 * the PT wiring (see {@link PhysicalTenantsConfiguredCondition}).
 */
@Configuration(proxyBeanMethods = false)
@Conditional(PhysicalTenantsConfiguredCondition.class)
public class PhysicalTenantWebappMappingConfiguration {

  @Bean
  public PhysicalTenantWebappRequestMappingHandlerMapping
      physicalTenantWebappRequestMappingHandlerMapping() {
    final var mapping = new PhysicalTenantWebappRequestMappingHandlerMapping();
    mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return mapping;
  }
}
