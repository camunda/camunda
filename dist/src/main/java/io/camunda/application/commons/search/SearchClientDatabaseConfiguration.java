/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.condition.ConditionalOnDatabaseNone;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedSearchClients;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.clients.auth.AnonymousResourceAccessController;
import io.camunda.search.clients.auth.DefaultResourceAccessProvider;
import io.camunda.search.clients.auth.DefaultTenantAccessProvider;
import io.camunda.search.clients.auth.DisabledResourceAccessProvider;
import io.camunda.search.clients.auth.DisabledTenantAccessProvider;
import io.camunda.search.clients.auth.DocumentBasedResourceAccessController;
import io.camunda.search.clients.auth.ResourceAccessDelegatingController;
import io.camunda.search.clients.impl.NoDBSearchClientsProxy;
import io.camunda.search.clients.reader.AuthorizationReader;
import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.search.clients.reader.impl.NoopAuthorizationReader;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.search.rdbms.RdbmsSearchClient;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccessProvider;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class SearchClientDatabaseConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = DatabaseConfig.ELASTICSEARCH,
      matchIfMissing = true)
  public ElasticsearchSearchClient elasticsearchSearchClient(
      final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new ElasticsearchSearchClient(elasticsearch);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = DatabaseConfig.OPENSEARCH)
  public OpensearchSearchClient opensearchSearchClient(final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    final var opensearch = connector.createClient();
    return new OpensearchSearchClient(opensearch);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = DatabaseConfig.RDBMS)
  public RdbmsSearchClient rdbmsSearchClient(final RdbmsService rdbmsService) {
    return new RdbmsSearchClient(rdbmsService);
  }

  @Bean
  @ConditionalOnBean(DocumentBasedSearchClient.class)
  public SearchClientBasedQueryExecutor searchClientBasedQueryExecutor(
      final DocumentBasedSearchClient searchClient,
      final ConnectConfiguration connectConfiguration) {
    final var descriptors =
        new IndexDescriptors(
            connectConfiguration.getIndexPrefix(),
            connectConfiguration.getTypeEnum().isElasticSearch());
    final var transformers = ServiceTransformers.newInstance(descriptors);
    return new SearchClientBasedQueryExecutor(searchClient, transformers);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.authorizations",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ResourceAccessProvider resourceAccessProvider(final AuthorizationChecker checker) {
    return new DefaultResourceAccessProvider(checker);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.authorizations",
      name = "enabled",
      havingValue = "false")
  public ResourceAccessProvider disabledResourceAccessProvider() {
    return new DisabledResourceAccessProvider();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.multiTenancy",
      name = "enabled",
      havingValue = "true")
  public TenantAccessProvider tenantAccessProvider() {
    return new DefaultTenantAccessProvider();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.security.multiTenancy",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  public TenantAccessProvider disabledTenantAccessProvider() {
    return new DisabledTenantAccessProvider();
  }

  @Bean
  public ResourceAccessController anonymousResourceAccessController() {
    return new AnonymousResourceAccessController();
  }

  @Bean
  @ConditionalOnBean(DocumentBasedSearchClient.class)
  public ResourceAccessController documentBasedResourceAccessController(
      final ResourceAccessProvider resourceAccessProvider,
      final TenantAccessProvider tenantAccessProvider) {
    return new DocumentBasedResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }

  @Bean
  @ConditionalOnBean(DocumentBasedSearchClient.class)
  public DocumentBasedSearchClients documentBasedSearchClients(
      final SearchClientReaders readers, final List<ResourceAccessController> controllers) {
    return new DocumentBasedSearchClients(
        readers, new ResourceAccessDelegatingController(controllers), null);
  }

  @Bean
  @ConditionalOnDatabaseNone
  public NoDBSearchClientsProxy noDBSearchClientsProxy() {
    return new NoDBSearchClientsProxy();
  }

  @Bean
  @ConditionalOnDatabaseNone
  public AuthorizationReader authorizationReader() {
    return new NoopAuthorizationReader();
  }
}
