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

  /**
   * Exposes the configured physical tenants to the REST layer so that {@code
   * /v2/physical-tenants/{physicalTenantId}/...} requests with an unknown id are rejected with HTTP
   * 404 before reaching any controller. Unconditional because physical tenants are storage-agnostic
   * — the gate must apply for every secondary-storage type.
   */
  @Bean
  public io.camunda.zeebe.gateway.rest.util.PhysicalTenantResolver
      gatewayRestPhysicalTenantResolver(final PhysicalTenantResolver physicalTenantResolver) {
    final Set<String> known = Set.copyOf(physicalTenantResolver.getAll().keySet());
    return known::contains;
  }
}
