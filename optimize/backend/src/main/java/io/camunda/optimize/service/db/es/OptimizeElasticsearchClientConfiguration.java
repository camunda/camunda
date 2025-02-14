/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonProvider;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(ElasticSearchCondition.class)
public class OptimizeElasticsearchClientConfiguration {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OptimizeElasticsearchClientConfiguration.class);
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final PluginRepository pluginRepository = new PluginRepository();
  private final OptimizeElasticsearchClient optimizeElasticsearchClient;
  private final ObjectMapper optimizeObjectMapper;

  public OptimizeElasticsearchClientConfiguration(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final ElasticSearchSchemaManager elasticSearchSchemaManager,
      final BackoffCalculator backoffCalculator,
      final ObjectMapper optimizeObjectMapper) {
    this.configurationService = configurationService;
    this.optimizeIndexNameService = optimizeIndexNameService;
    this.elasticSearchSchemaManager = elasticSearchSchemaManager;
    this.optimizeObjectMapper = optimizeObjectMapper;

    // Create client within the constructor and reuse it later for the beans
    optimizeElasticsearchClient = createOptimizeElasticsearchClient(backoffCalculator);
  }

  @Bean
  public OptimizeElasticsearchClient optimizeElasticsearchClient() {
    return optimizeElasticsearchClient;
  }

  @Bean
  public ElasticsearchClient elasticsearchClient() {
    return optimizeElasticsearchClient.elasticsearchClient();
  }

  @Bean
  public JacksonJsonProvider jacksonJsonProvider() {
    return new JacksonJsonProvider(new JacksonJsonpMapper(optimizeObjectMapper));
  }

  public OptimizeElasticsearchClient createOptimizeElasticsearchClient(
      final BackoffCalculator backoffCalculator) {
    try {
      return OptimizeElasticsearchClientFactory.create(
          configurationService,
          optimizeIndexNameService,
          elasticSearchSchemaManager,
          backoffCalculator,
          pluginRepository,
          optimizeObjectMapper);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }
}
