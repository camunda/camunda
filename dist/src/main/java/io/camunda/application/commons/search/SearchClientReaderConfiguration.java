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
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class SearchClientReaderConfiguration {

  @Bean
  public Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors(
      final PhysicalTenantResolver physicalTenantResolver) {
    return physicalTenantResolver.mapValues(
        physicalTenantCfg -> {
          final var secondaryStorage = physicalTenantCfg.getData().getSecondaryStorage();
          return new IndexDescriptors(
              secondaryStorage
                  .getElasticsearchOrOpensearch()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Secondary storage is not Elasticsearch or OpenSearch"))
                  .getIndexPrefix(),
              secondaryStorage.getType().isElasticSearch());
        });
  }

  /**
   * SPIKE (ADR-0005): route authorization reads to the physical tenant in context instead of
   * binding to the {@code default} tenant's reader (#55252). Both authorization-read consumers — the
   * control-plane {@code AuthorizationRepositoryAdapter} and the data-plane {@code
   * SearchAuthorizationScopeRepository} — inject this single bean, so re-pointing it here covers both
   * paths. Was: {@code requireDefault(physicalTenantSearchClientReaders.readersByPhysicalTenant(),
   * ...).authorizationReader();} (default-pinned).
   */
  @Bean
  public AuthorizationReader authorizationReader(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
    final Map<String, AuthorizationReader> readersByPhysicalTenant =
        physicalTenantSearchClientReaders.readersByPhysicalTenant().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().authorizationReader()));
    return new PhysicalTenantRoutingAuthorizationReader(readersByPhysicalTenant);
  }
}
