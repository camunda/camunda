/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.search.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.authentication.handler.session.ConditionalOnSessionPersistence;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.es.clients.ElasticsearchSessionDocumentClient;
import io.camunda.search.es.clients.RetryElasticsearchClient;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnRestGatewayEnabled
@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = "elasticsearch",
    matchIfMissing = true)
public class ElasticSearchSearchClientConfiguration {

  @Bean
  public ElasticsearchSearchClient elasticsearchSearchClient(
      final SearchClientProperties configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    final var elasticsearch = connector.createClient();
    return new ElasticsearchSearchClient(elasticsearch);
  }

  @Bean(destroyMethod = "close")
  public RestHighLevelClient restHighLevelClient(final SearchClientProperties configuration) {
    // some weird error when ELS sets available processors number for Netty - see
    // https://discuss.elastic.co/t/elasticsearch-5-4-1-availableprocessors-is-already-set/88036/3
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    final ElasticsearchConnector connector = new ElasticsearchConnector(configuration);
    return connector.createRestHighLevelClient();
  }

  @Bean
  public RetryElasticsearchClient retryElasticsearchClient(
      final RestHighLevelClient esClient, final ObjectMapper objectMapper) {
    return new RetryElasticsearchClient(esClient, objectMapper);
  }

  @Bean
  @ConditionalOnSessionPersistence
  public ElasticsearchSessionDocumentClient elasticsearchSessionDocumentClient(
      final RetryElasticsearchClient client) {
    final ElasticsearchSessionDocumentClient documentClient =
        new ElasticsearchSessionDocumentClient(client);
    documentClient.setup();
    return documentClient;
  }
}
