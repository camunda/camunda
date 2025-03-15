/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.SearchClients;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import io.camunda.search.rdbms.RdbmsSearchClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
public class SearchClientConfiguration {

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = DatabaseConfig.RDBMS)
  public RdbmsSearchClient rdbmsSearchClient(final RdbmsService rdbmsService) {
    return new RdbmsSearchClient(rdbmsService);
  }

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
    final var elasticsearch = connector.createClient();
    return new OpensearchSearchClient(elasticsearch);
  }

  @Bean
  @ConditionalOnBean(DocumentBasedSearchClient.class)
  public SearchClients searchClients(
      final DocumentBasedSearchClient searchClient,
      final ConnectConfiguration connectConfiguration) {
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(
            connectConfiguration.getIndexPrefix(),
            connectConfiguration.getTypeEnum().isElasticSearch());
    return new SearchClients(searchClient, indexDescriptors);
  }
}
