/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.schema.ElasticsearchSchemaMigrationStatusProvider;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.zeebe.util.VersionUtil;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Elasticsearch/OpenSearch {@code elasticsearchSchemaMigrated} upgrade-readiness
 * condition (camunda/product-hub#3067), built from the same per-physical-tenant {@link
 * SearchEngineConfiguration} map {@link SearchEngineDatabaseConfiguration} already uses for schema
 * initialization.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class ElasticsearchSchemaMigrationStatusProviderConfiguration {

  /**
   * The returned provider implements {@link AutoCloseable}; Spring infers and calls its {@code
   * close()} on context shutdown to release the long-lived per-tenant search engine clients it
   * opens.
   */
  @Bean
  public ElasticsearchSchemaMigrationStatusProvider elasticsearchSchemaMigrationStatusProvider(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant) {
    return ElasticsearchSchemaMigrationStatusProvider.fromConfigs(
        searchEngineConfigurationsByTenant, VersionUtil.getVersion());
  }
}
