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
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class NativeSearchClientsConfiguration {

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.elasticsearch)
  public ElasticsearchClient elasticsearchClient(final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    return connector.createClient();
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.opensearch)
  public OpenSearchClient openSearchClient(final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    return connector.createClient();
  }

  @Bean
  @ConditionalOnSecondaryStorageType(SecondaryStorageType.opensearch)
  public OpenSearchAsyncClient openSearchAsyncClient(final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    return connector.createAsyncClient();
  }
}
