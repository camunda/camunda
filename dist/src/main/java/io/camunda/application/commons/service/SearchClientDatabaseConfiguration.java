/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.application.commons.service.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.search.connect.SearchClientProvider;
import io.camunda.search.connect.SearchClientProvider.SearchClientProviders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "zeebe.broker.gateway",
    name = "enable",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(SearchClientProperties.class)
public class SearchClientDatabaseConfiguration {

  private final ConnectConfiguration configuration;

  @Autowired
  public SearchClientDatabaseConfiguration(final SearchClientProperties configuration) {
    this.configuration = configuration;
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "camunda.database",
      name = "type",
      havingValue = "elasticsearch",
      matchIfMissing = true)
  public SearchClientProvider elasticsearchClientProvider() {
    return SearchClientProviders::createElasticsearchProvider;
  }

  @Bean
  @ConditionalOnProperty(prefix = "camunda.database", name = "type", havingValue = "opensearch")
  public SearchClientProvider opensearchClientProvider() {
    return SearchClientProviders::createOpensearchProvider;
  }

  @Bean
  public CamundaSearchClient camundaSearchClient(final SearchClientProvider searchClientProvider) {
    return searchClientProvider.apply(configuration);
  }

  @ConfigurationProperties("camunda.database")
  public static final class SearchClientProperties extends ConnectConfiguration {}
}
