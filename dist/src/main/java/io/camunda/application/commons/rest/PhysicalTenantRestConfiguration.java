/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider.JobMetrics;
import io.camunda.zeebe.gateway.rest.config.PhysicalTenantRestConfigProvider.TenantRestConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class PhysicalTenantRestConfiguration {

  @Bean
  public PhysicalTenantRestConfigProvider physicalTenantRestConfigProvider(
      final PhysicalTenantResolver physicalTenantResolver,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    final int globalMaxNameFieldLength = gatewayRestConfiguration.getMaxNameFieldLength();
    final Map<String, TenantRestConfig> configs = new HashMap<>();
    physicalTenantResolver
        .getAll()
        .forEach(
            (tenantId, tenantConfig) -> {
              final var jm = tenantConfig.getMonitoring().getMetrics().getJobMetrics();
              final int maxNameFieldLength =
                  tenantConfig.getData().getSecondaryStorage().getType()
                          == SecondaryStorageType.rdbms
                      ? tenantConfig
                          .getData()
                          .getSecondaryStorage()
                          .getRdbms()
                          .getMaxVarcharFieldLength()
                      : globalMaxNameFieldLength;
              configs.put(
                  tenantId,
                  new TenantRestConfig(
                      maxNameFieldLength,
                      new JobMetrics(
                          jm.isEnabled(),
                          jm.getExportInterval(),
                          jm.getMaxWorkerNameLength(),
                          jm.getMaxJobTypeLength(),
                          jm.getMaxTenantIdLength(),
                          jm.getMaxUniqueKeys())));
            });
    return tenantId -> {
      final var cfg = configs.get(tenantId);
      if (cfg == null) {
        throw new IllegalArgumentException("No REST config for physical tenant: " + tenantId);
      }
      return cfg;
    };
  }
}
