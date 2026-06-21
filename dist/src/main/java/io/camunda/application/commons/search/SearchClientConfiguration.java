/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.impl.NoDBSearchClientsProxy;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.impl.NoopAuthorizationReader;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public CamundaSearchClients camundaSearchClients(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final Optional<PhysicalTenantResourceAccessControllers>
          physicalTenantResourceAccessControllers) {
    return new CamundaSearchClients(
        physicalTenantSearchClientReaders.readersByPhysicalTenant(),
        physicalTenantResourceAccessControllers
            .map(PhysicalTenantResourceAccessControllers::controllersByPhysicalTenant)
            .orElseGet(() -> failFastControllers(physicalTenantSearchClientReaders)));
  }

  /**
   * Fallback for non-web contexts (e.g. Restore, engine-only integration tests) where the per-PT
   * {@link PhysicalTenantResourceAccessControllers} bean is not created. Such contexts do not
   * perform authorized data-plane reads; an empty delegating controller keeps the context startable
   * while failing fast on any accidental read.
   */
  private static Map<String, ResourceAccessController> failFastControllers(
      final PhysicalTenantSearchClientReaders readers) {
    return readers.readersByPhysicalTenant().keySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                tenantId -> tenantId,
                tenantId -> new ResourceAccessDelegatingController(List.of())));
  }
}
