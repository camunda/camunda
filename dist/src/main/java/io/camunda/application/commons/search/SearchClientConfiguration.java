/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static io.camunda.configuration.physicaltenants.PhysicalTenantResolver.DEFAULT_PHYSICAL_TENANT_ID;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.impl.NoDBSearchClientsProxy;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.impl.NoopAuthorizationReader;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SearchClientConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.elasticsearch)
  public ElasticsearchSearchClient elasticsearchSearchClient(
      final ElasticsearchClient elasticsearchClient) {
    return new ElasticsearchSearchClient(elasticsearchClient);
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.opensearch)
  public OpensearchSearchClient opensearchSearchClient(final OpenSearchClient openSearchClient) {
    return new OpensearchSearchClient(openSearchClient);
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public NoDBSearchClientsProxy noDBSearchClientsProxy() {
    return new NoDBSearchClientsProxy();
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public AuthorizationReader authorizationReader() {
    return new NoopAuthorizationReader();
  }

  @Bean
  @ConditionalOnSecondaryStorageType({
    SecondaryStorageType.rdbms,
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public CamundaSearchClients camundaSearchClients(
      final List<PhysicalTenantSearchClientReaders> physicalTenantSearchClientReaders,
      final List<ResourceAccessController> resourceAccessControllers) {
    return new CamundaSearchClients(
        mergeByPhysicalTenant(physicalTenantSearchClientReaders),
        new ResourceAccessDelegatingController(resourceAccessControllers));
  }

  static Map<String, SearchClientReaders> mergeByPhysicalTenant(
      final List<PhysicalTenantSearchClientReaders> holders) {
    final var merged = new LinkedHashMap<String, SearchClientReaders>();
    for (final var holder : holders) {
      holder
          .readersByPhysicalTenant()
          .forEach(
              (physicalTenantId, readers) -> {
                if (merged.putIfAbsent(physicalTenantId, readers) != null) {
                  throw new IllegalStateException(
                      "Physical tenant '%s' is backed by more than one storage configuration"
                          .formatted(physicalTenantId));
                }
              });
    }
    if (!merged.containsKey(DEFAULT_PHYSICAL_TENANT_ID)) {
      throw new IllegalStateException(
          "Missing '%s' physical tenant; the global secondary-storage configuration must provide it. Known physical tenants: %s"
              .formatted(DEFAULT_PHYSICAL_TENANT_ID, merged.keySet()));
    }
    return Map.copyOf(merged);
  }
}
