/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.schema.ElasticsearchSchemaMigrationStatusProvider;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.zeebe.util.VersionUtil;
import java.util.LinkedHashMap;
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
   * The provider reads through each tenant's shared search client, which this node opens only when
   * the tenant is used, rather than holding a client per tenant of its own.
   */
  @Bean
  public ElasticsearchSchemaMigrationStatusProvider elasticsearchSchemaMigrationStatusProvider(
      @Qualifier("searchEngineConfigurationsByTenant")
          final Map<String, SearchEngineConfiguration> searchEngineConfigurationsByTenant,
      final SearchClients searchClients) {
    final var engineClients = new LinkedHashMap<String, SearchEngineClient>();
    searchEngineConfigurationsByTenant.forEach(
        (tenantId, config) -> {
          final var objectMapper = new JacksonConfiguration(config.connect()).createObjectMapper();
          final var es = searchClients.esClients().get(tenantId);
          if (es != null) {
            engineClients.put(tenantId, new ElasticsearchEngineClient(es, objectMapper));
          } else {
            engineClients.put(
                tenantId,
                new OpensearchEngineClient(searchClients.osClients().get(tenantId), objectMapper));
          }
        });
    return ElasticsearchSchemaMigrationStatusProvider.fromClients(
        searchEngineConfigurationsByTenant, engineClients, VersionUtil.getVersion());
  }
}
