/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchEngineDatabaseConfiguration {

  // Still required as an unqualified bean: io.camunda.operate.management.IndicesCheck autowires
  // a plain SearchEngineConfiguration for the default physical tenant.
  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant) {
    return searchEngineConfigurationsByTenant.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  @Bean
  public SearchEngineSchemaInitializer searchEngineSchemaInitializer(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant,
      @Qualifier("physicalTenantScopedIndexDescriptors")
          final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors,
      final MeterRegistry meterRegistry,
      @Autowired(required = false)
          final Broker broker, // if present, then it will ensure that the broker is started first
      @Autowired(required = false) final BrokerCfg brokerCfg) {
    final boolean isGatewayEnabled = brokerCfg == null || brokerCfg.getGateway().isEnable();
    final boolean healthCheckEnabled =
        searchEngineConfigurationsByTenant
            .get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID)
            .schemaManager()
            .isHealthCheckEnabled();
    return new SearchEngineSchemaInitializer(
        searchEngineConfigurationsByTenant,
        physicalTenantScopedIndexDescriptors,
        meterRegistry,
        isGatewayEnabled,
        healthCheckEnabled);
  }
}
