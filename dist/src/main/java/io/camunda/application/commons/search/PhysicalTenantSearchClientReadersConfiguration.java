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
import io.camunda.search.clients.CamundaSearchClients;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.cache.ProcessCache;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.tenant.SearchClients;
import io.camunda.search.connect.tenant.TenantConnectConfigResolver;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.PhysicalTenantResolver;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnSecondaryStorageType({
  SecondaryStorageType.elasticsearch,
  SecondaryStorageType.opensearch
})
public class PhysicalTenantSearchClientReadersConfiguration {

  @Bean
  public TenantConnectConfigResolver tenantConnectConfigResolver(
      final ConnectConfiguration connectConfiguration) {
    return new TenantConnectConfigResolver(
        Map.of(TenantConnectConfigResolver.DEFAULT_TENANT_ID, connectConfiguration));
  }

  @Bean
  public SearchClients searchClients(
      final TenantConnectConfigResolver tenantConnectConfigResolver) {
    return SearchClients.from(tenantConnectConfigResolver.tenantConfigs());
  }

  @Bean
  public Map<String, DocumentBasedSearchClient> physicalTenantDocumentSearchClients(
      final SearchClients searchClients) {
    final var clients = new LinkedHashMap<String, DocumentBasedSearchClient>();
    searchClients
        .esClients()
        .forEach(
            (tenantId, esClient) -> clients.put(tenantId, new ElasticsearchSearchClient(esClient)));
    searchClients
        .osClients()
        .forEach(
            (tenantId, osClient) -> clients.put(tenantId, new OpensearchSearchClient(osClient)));
    return Map.copyOf(clients);
  }

  @Bean
  public Map<String, SearchClientBasedQueryExecutor> physicalTenantQueryExecutors(
      final Map<String, DocumentBasedSearchClient> physicalTenantDocumentSearchClients,
      final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors) {
    final var executors = new LinkedHashMap<String, SearchClientBasedQueryExecutor>();
    physicalTenantDocumentSearchClients.forEach(
        (tenantId, docClient) -> {
          final var descriptors = physicalTenantScopedIndexDescriptors.get(tenantId);
          if (descriptors == null) {
            throw new IllegalStateException(
                "Missing IndexDescriptors for physical tenant '" + tenantId + "'");
          }
          final var transformers = ServiceTransformers.newInstance(descriptors);
          executors.put(tenantId, new SearchClientBasedQueryExecutor(docClient, transformers));
        });
    return Map.copyOf(executors);
  }

  @Bean
  public Map<String, SearchClientReaders> physicalTenantSearchClientReaders(
      final Map<String, SearchClientBasedQueryExecutor> physicalTenantQueryExecutors,
      final Map<String, IndexDescriptors> physicalTenantScopedIndexDescriptors,
      final GatewayRestConfiguration config) {
    final var cacheConfig =
        new ProcessCache.Configuration(
            config.getProcessCache().getMaxSize(),
            config.getProcessCache().getExpirationIdleMillis());
    final var readersByTenant = new LinkedHashMap<String, SearchClientReaders>();
    physicalTenantQueryExecutors.forEach(
        (tenantId, executor) -> {
          final var descriptors = physicalTenantScopedIndexDescriptors.get(tenantId);
          if (descriptors == null) {
            throw new IllegalStateException(
                "Missing IndexDescriptors for physical tenant '" + tenantId + "'");
          }
          readersByTenant.put(
              tenantId, SearchClientReadersFactory.create(executor, descriptors, cacheConfig));
        });
    return Map.copyOf(readersByTenant);
  }

  @Bean
  public CamundaSearchClients camundaSearchClients(
      final Map<String, SearchClientReaders> physicalTenantSearchClientReaders,
      final List<ResourceAccessController> resourceAccessControllers) {
    return new CamundaSearchClients(
        physicalTenantSearchClientReaders,
        new ResourceAccessDelegatingController(resourceAccessControllers));
  }

  /**
   * Exposes the configured physical tenants to the REST layer so that {@code
   * /v2/physical-tenants/{physicalTenantId}/...} requests with an unknown id are rejected with HTTP
   * 404 before reaching any controller.
   */
  @Bean
  public PhysicalTenantResolver physicalTenantResolver(
      final TenantConnectConfigResolver tenantConnectConfigResolver) {
    final Set<String> known = Set.copyOf(tenantConnectConfigResolver.tenantConfigs().keySet());
    return known::contains;
  }

  /**
   * Same source-of-truth as {@link #physicalTenantResolver(TenantConnectConfigResolver)} but
   * exposed via the MCP gateway's local {@link
   * io.camunda.gateway.mcp.context.PhysicalTenantResolver} type so the {@code gateway-mcp} module
   * can validate {@code /mcp/physical-tenants/{physicalTenantId}/...} requests without depending on
   * {@code zeebe-gateway-rest}.
   */
  @Bean
  public io.camunda.gateway.mcp.context.PhysicalTenantResolver mcpPhysicalTenantResolver(
      final TenantConnectConfigResolver tenantConnectConfigResolver) {
    final Set<String> known = Set.copyOf(tenantConnectConfigResolver.tenantConfigs().keySet());
    return known::contains;
  }
}
