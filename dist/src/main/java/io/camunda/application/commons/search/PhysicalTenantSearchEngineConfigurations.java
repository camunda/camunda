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
import java.util.Collections;
import java.util.LinkedHashMap;
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
      final PhysicalTenantResolver physicalTenantResolver,
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties,
      final SearchEngineSchemaManagerProperties searchEngineSchemaManagerProperties) {
    final var byTenant = new LinkedHashMap<String, SearchEngineConfiguration>();
    for (final String tenantId : physicalTenantResolver.known()) {
      byTenant.put(
          tenantId,
          // The default tenant must keep resolving from the root properties beans rather than
          // through convert(physicalTenantResolver.forPhysicalTenant(tenantId)) like other
          // tenants: those beans are still bound from the legacy `camunda.database.*` prefix,
          // which several call sites (e.g. test infra injecting a dynamic ES/OS URL) still rely
          // on, and which isn't mirrored into the unified `camunda.data.secondary-storage.*` tree
          // that PhysicalTenantResolver/Converter read from. Dropping this fell back to a
          // non-existent local Elasticsearch and hung schema initialization at startup. See
          // https://github.com/camunda/camunda/issues/57950 before removing this special case.
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

  static SearchEngineConfiguration buildConfiguration(
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
