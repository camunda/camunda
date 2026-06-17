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
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.clients.auth.DefaultResourceAccessProvider;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.search.clients.auth.DocumentBasedResourceAccessController;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.impl.NoDBSearchClientsProxy;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.impl.NoopAuthorizationReader;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.core.authz.ResourceAccessController;
import io.camunda.security.core.authz.ResourceAccessProvider;
import io.camunda.security.core.authz.TenantAccessProvider;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import java.util.HashMap;
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
    SecondaryStorageType.elasticsearch,
    SecondaryStorageType.opensearch
  })
  public CamundaSearchClients camundaSearchClients(
      final PhysicalTenantSearchClientReaders physicalTenantSearchClientReaders,
      final CamundaSecurityLibraryProperties cslProperties,
      final TenantAccessProvider tenantAccessProvider) {
    final Map<String, ResourceAccessController> racByTenant = new HashMap<>();
    for (final Map.Entry<String, SearchClientReaders> entry :
        physicalTenantSearchClientReaders.readersByPhysicalTenant().entrySet()) {
      final String tenantId = entry.getKey();
      final AuthorizationReader reader = entry.getValue().authorizationReader();
      final var scopeRepo = new SearchAuthorizationScopeRepository(reader);
      final var checker = new AuthorizationChecker(scopeRepo);
      final ResourceAccessProvider provider =
          cslProperties.getAuthorizations().isEnabled()
              ? new DefaultResourceAccessProvider(checker)
              : new DisabledResourceAccessProvider();
      final ResourceAccessController rac =
          new DocumentBasedResourceAccessController(provider, tenantAccessProvider);
      final ResourceAccessController delegating =
          new ResourceAccessDelegatingController(
              List.of(new AnonymousResourceAccessController(), rac));
      racByTenant.put(tenantId, delegating);
    }
    return new CamundaSearchClients(
        physicalTenantSearchClientReaders.readersByPhysicalTenant(), racByTenant);
  }
}
