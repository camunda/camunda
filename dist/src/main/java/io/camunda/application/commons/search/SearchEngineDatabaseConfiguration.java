/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineIndexPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionPropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerPropertiesOverride;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.configuration.beans.SearchEngineIndexProperties;
import io.camunda.configuration.beans.SearchEngineRetentionProperties;
import io.camunda.configuration.beans.SearchEngineSchemaManagerProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.configuration.DatabaseType;
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

  @Bean
  public Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant(
      final PhysicalTenantResolver physicalTenantResolver,
      final SearchEngineConnectPropertiesOverride connectOverride,
      final SearchEngineIndexPropertiesOverride indexOverride,
      final SearchEngineRetentionPropertiesOverride retentionOverride,
      final SearchEngineSchemaManagerPropertiesOverride schemaManagerOverride) {
    return physicalTenantResolver.mapValues(
        tenantCamunda ->
            buildConfiguration(
                connectOverride.searchEngineConnectProperties(tenantCamunda),
                indexOverride.searchEngineIndexProperties(tenantCamunda),
                retentionOverride.searchEngineRetentionProperties(tenantCamunda),
                schemaManagerOverride.searchEngineSchemaManagerProperties(tenantCamunda)));
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
    return new SearchEngineSchemaInitializer(
        searchEngineConfigurationsByTenant,
        physicalTenantScopedIndexDescriptors,
        meterRegistry,
        isGatewayEnabled);
  }

  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties,
      final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
    return buildConfiguration(
        searchEngineConnectProperties,
        searchEngineIndexProperties,
        searchEngineRetentionProperties,
        searchEngineSchemaManagerProperties);
  }

  private static SearchEngineConfiguration buildConfiguration(
      final SearchEngineConnectProperties connect,
      final SearchEngineIndexProperties index,
      final SearchEngineRetentionProperties retention,
      final SearchEngineSchemaManagerProperties schemaManager) {

    // Override schema creation if database type is "none"
    final DatabaseType databaseType = connect.getTypeEnum();
    if (DatabaseConfig.NONE.equalsIgnoreCase(databaseType.name())) {
      schemaManager.setCreateSchema(false);
    }

    return SearchEngineConfiguration.of(
        b -> b.connect(connect).index(index).retention(retention).schemaManager(schemaManager));
  }
}
