/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.se;

import io.camunda.application.commons.se.SearchEngineDatabaseConfiguration.SearchEngineConnectProperties;
import io.camunda.application.commons.se.SearchEngineDatabaseConfiguration.SearchEngineIndexProperties;
import io.camunda.application.commons.se.SearchEngineDatabaseConfiguration.SearchEngineRetentionProperties;
import io.camunda.db.se.config.ConnectConfiguration;
import io.camunda.db.se.config.DatabaseConfig;
import io.camunda.db.se.config.IndexSettings;
import io.camunda.db.se.config.RetentionConfiguration;
import io.camunda.db.se.config.SearchEngineConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.clients.ElasticsearchSearchClient;
import io.camunda.search.os.clients.OpensearchSearchClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(SearchEngineEnabledCondition.class)
@EnableConfigurationProperties({
  SearchEngineConnectProperties.class,
  SearchEngineIndexProperties.class,
  SearchEngineRetentionProperties.class,
})
public class SearchEngineDatabaseConfiguration {

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
  public SearchEngineConfiguration searchEngineConfiguration(
      final SearchEngineConnectProperties searchEngineConnectProperties,
      final SearchEngineIndexProperties searchEngineIndexProperties,
      final SearchEngineRetentionProperties searchEngineRetentionProperties) {
    return new SearchEngineConfiguration(
        searchEngineConnectProperties,
        searchEngineIndexProperties,
        searchEngineRetentionProperties);
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchEngineConnectProperties extends ConnectConfiguration {}

  @ConfigurationProperties("camunda.database.index")
  public static final class SearchEngineIndexProperties extends IndexSettings {}

  @ConfigurationProperties("camunda.database.retention")
  public static final class SearchEngineRetentionProperties extends RetentionConfiguration {}
}
