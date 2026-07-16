/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride.Converter;
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
import java.util.Collections;
import java.util.LinkedHashMap;
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
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties,
      final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
    final var byTenant = new LinkedHashMap<String, SearchEngineConfiguration>();
    for (final String tenantId : physicalTenantResolver.known()) {
      byTenant.put(
          tenantId,
          PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID.equals(tenantId)
              ? buildConfiguration(
                  searchEngineConnectProperties,
                  searchEngineIndexProperties,
                  searchEngineRetentionProperties,
                  searchEngineSchemaManagerProperties)
              : convert(physicalTenantResolver.forPhysicalTenant(tenantId)));
    }
    return Collections.unmodifiableMap(byTenant);
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

  // Still required as an unqualified bean: io.camunda.operate.management.IndicesCheck autowires
  // a plain SearchEngineConfiguration for the default physical tenant.
  @Bean
  public SearchEngineConfiguration searchEngineConfiguration(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant) {
    return searchEngineConfigurationsByTenant.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
  }

  private static SearchEngineConfiguration convert(final Camunda tenantCamunda) {
    final var index = new SearchEngineIndexProperties();
    SearchEngineIndexPropertiesOverride.applyTo(tenantCamunda, index);
    final var retention = new SearchEngineRetentionProperties();
    SearchEngineRetentionPropertiesOverride.applyTo(tenantCamunda, retention);
    final var schemaManager = new SearchEngineSchemaManagerProperties();
    SearchEngineSchemaManagerPropertiesOverride.applyTo(tenantCamunda, schemaManager);
    return buildConfiguration(
        new Converter(tenantCamunda).convert(), index, retention, schemaManager);
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
