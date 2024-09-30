/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.search.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
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
}
