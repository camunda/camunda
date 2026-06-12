/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.api.physicaltenants.PhysicalTenantIds;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.util.Map;
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

  @Bean
  public AuthorizationReader authorizationReader(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders) {
    return requireDefault(
            physicalTenantSearchClientReaders.readersByPhysicalTenant(),
            "physicalTenantSearchClientReaders")
        .authorizationReader();
  }

  private static <T> T requireDefault(final Map<String, T> beansByTenant, final String beanName) {
    final var defaultBean = beansByTenant.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
    if (defaultBean == null) {
      throw new IllegalStateException(
          "Missing '"
              + PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID
              + "' tenant entry in "
              + beanName);
    }
    return defaultBean;
  }
}
