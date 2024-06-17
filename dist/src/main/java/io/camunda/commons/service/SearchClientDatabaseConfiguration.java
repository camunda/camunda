/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.commons.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.commons.service.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.SearchClientProvider;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.jackson.JacksonConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchClientProperties.class)
public class SearchClientDatabaseConfiguration {

  private final ConnectConfiguration configuration;

  @Autowired
  public SearchClientDatabaseConfiguration(final SearchClientProperties configuration) {
    this.configuration = configuration;
  }

  @Bean
  @Qualifier("searchClientObjectMapper")
  public ObjectMapper searchClientObjectMapper() {
    final var jacksonConfiguration = new JacksonConfiguration(configuration);
    return jacksonConfiguration.createObjectMapper();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = "elasticsearch",
      matchIfMissing = true)
  public SearchClientProvider elasticsearchClientProvider() {
    return (config, mapper) -> {
      final var connector = new ElasticsearchConnector(config, mapper);
      final var elasticsearch = connector.createClient();
      return new ElasticsearchSearchClient(elasticsearch);
    };
  }

  @Bean
  @ConditionalOnProperty(prefix = "camunda.database", name = "type", havingValue = "opensearch")
  public SearchClientProvider opensearchClientProvider() {
    return (config, mapper) -> {
      final var connector = new OpensearchConnector(config, mapper);
      final var opensearch = connector.createClient();
      return new OpensearchSearchClient(opensearch);
    };
  }

  @Bean
  public CamundaSearchClient camundaSearchClient(
      final SearchClientProvider searchClientProvider,
      final @Qualifier("searchClientObjectMapper") ObjectMapper searchClientObjectMapper) {
    return searchClientProvider.apply(configuration, searchClientObjectMapper);
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchClientProperties extends ConnectConfiguration {}
}
