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
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.connect.tenant.TenantConnectConfigResolver;
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
  public IndexDescriptors indexDescriptors(
      final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors) {
    return requireDefault(
        physicalTenantScopedIndexDescriptors, "physicalTenantScopedIndexDescriptors");
  }

  @Bean
  public Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors(
      final TenantConnectConfigResolver tenantConnectConfigResolver) {
    return tenantConnectConfigResolver.tenantConfigs().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    new IndexDescriptors(
                        entry.getValue().getIndexPrefix(),
                        entry.getValue().getTypeEnum().isElasticSearch())));
  }

  @Bean
  public SearchClientBasedQueryExecutor searchClientBasedQueryExecutor(
      final Map<String, SearchClientBasedQueryExecutor> physicalTenantQueryExecutors) {
    return requireDefault(physicalTenantQueryExecutors, "physicalTenantQueryExecutors");
  }

  @Bean
  public SearchClientReaders documentReaders(
      final Map<String, SearchClientReaders> physicalTenantSearchClientReaders) {
    return requireDefault(physicalTenantSearchClientReaders, "physicalTenantSearchClientReaders");
  }

  @Bean
  public AuthorizationReader authorizationReader(final SearchClientReaders documentReaders) {
    return documentReaders.authorizationReader();
  }

  private static <T> T requireDefault(final Map<String, T> beansByTenant, final String beanName) {
    final var defaultBean = beansByTenant.get(TenantConnectConfigResolver.DEFAULT_TENANT_ID);
    if (defaultBean == null) {
      throw new IllegalStateException(
          "Missing '"
              + TenantConnectConfigResolver.DEFAULT_TENANT_ID
              + "' tenant entry in "
              + beanName);
    }
    return defaultBean;
  }
}
