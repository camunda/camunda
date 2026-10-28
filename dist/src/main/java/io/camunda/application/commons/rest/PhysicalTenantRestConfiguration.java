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
import io.camunda.spring.utils.PhysicalTenantCpuCharger;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class PhysicalTenantRestConfiguration {

  /**
   * Charges the CPU time of REST work to the CPU budget of its physical tenant. A gateway without a
   * broker has no CPU budget, and nothing is charged.
   */
  @Bean
  public PhysicalTenantCpuCharger physicalTenantCpuCharger(
      final ObjectProvider<SpringBrokerBridge> brokerBridge) {
    final var bridge = brokerBridge.getIfAvailable();
    if (bridge == null) {
      return PhysicalTenantCpuCharger.none();
    }
    return new PhysicalTenantCpuCharger(
        (physicalTenantId, cpuNanos) ->
            bridge.getCpuBudget(physicalTenantId).ifPresent(budget -> budget.charge(cpuNanos)));
  }

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
