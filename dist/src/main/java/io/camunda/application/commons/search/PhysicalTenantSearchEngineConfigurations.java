/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beanoverrides.SearchEngineConnectConverter;
import io.camunda.configuration.beanoverrides.SearchEngineIndexConverter;
import io.camunda.configuration.beanoverrides.SearchEngineRetentionConverter;
import io.camunda.configuration.beanoverrides.SearchEngineSchemaManagerConverter;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the per-physical-tenant {@link SearchEngineConfiguration} map. Kept separate from {@link
 * SearchEngineDatabaseConfiguration} so that applications that need the per-tenant configurations
 * without schema initialization (e.g. the standalone backup manager) can register this class alone.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class PhysicalTenantSearchEngineConfigurations {

  @Bean
  public Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant(
      final PhysicalTenantResolver physicalTenantResolver) {
    return physicalTenantResolver.mapValues(PhysicalTenantSearchEngineConfigurations::convert);
  }

  private static SearchEngineConfiguration convert(final Camunda tenantCamunda) {
    return SearchEngineConfiguration.of(
        b ->
            b.connect(SearchEngineConnectConverter.convert(tenantCamunda))
                .index(SearchEngineIndexConverter.convert(tenantCamunda))
                .retention(SearchEngineRetentionConverter.convert(tenantCamunda))
                .schemaManager(SearchEngineSchemaManagerConverter.convert(tenantCamunda)));
  }
}
