/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.gateway.mapping.http.physicaltenants.PhysicalTenantIds;
import io.camunda.zeebe.gateway.rest.interceptor.PhysicalTenantInterceptor;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Aggregator for the unified configuration module. Importing this single class registers {@code
 * UnifiedConfiguration}, {@code UnifiedConfigurationHelper}, and every {@code *Override}
 * configuration under {@code io.camunda.configuration}. Each override self-gates via
 * {@code @Profile} or other conditional annotations, so only the beans relevant to the active
 * profiles are instantiated.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("io.camunda.configuration")
public class UnifiedConfigurationModule {

  @Bean
  public PhysicalTenantResolver physicalTenantResolver(
      final Environment environment, final Camunda camunda) {
    return PhysicalTenantResolver.of(environment, camunda);
  }

  @Bean
  public PhysicalTenantIds physicalTenantIds(final PhysicalTenantResolver physicalTenantResolver) {
    return () -> Set.copyOf(physicalTenantResolver.getAll().keySet());
  }

  /**
   * Exposes the physical-tenant interceptor to the REST and MCP gateways so that {@code
   * /physical-tenants/{physicalTenantId}/...} requests with an unknown id are rejected with HTTP
   * 404 before reaching any controller.
   */
  @Bean
  public PhysicalTenantInterceptor physicalTenantInterceptor(
      final PhysicalTenantIds physicalTenantIds) {
    return new PhysicalTenantInterceptor(id -> physicalTenantIds.known().contains(id));
  }
}
