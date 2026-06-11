/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.configuration.beanoverrides.GatewayRestPropertiesOverride;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class PhysicalTenantRestConfiguration {

  @Bean
  public PhysicalTenantRestConfigProvider physicalTenantRestConfigProvider(
      final PhysicalTenantResolver physicalTenantResolver) {
    final var configs =
        physicalTenantResolver.mapValues(
            physicalTenantCfg ->
                new GatewayRestPropertiesOverride.Converter(physicalTenantCfg).convert());
    return tenantId -> {
      final var cfg = configs.get(tenantId);
      if (cfg == null) {
        throw new IllegalArgumentException("No REST config for physical tenant: " + tenantId);
      }
      return cfg;
    };
  }
}
