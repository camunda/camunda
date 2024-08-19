/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es;

import io.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(ElasticSearchCondition.class)
public class OptimizeElasticsearchClientConfiguration {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeElasticsearchClientConfiguration.class);
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;

  public OptimizeElasticsearchClientConfiguration(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final ElasticSearchSchemaManager elasticSearchSchemaManager) {
    this.configurationService = configurationService;
    this.optimizeIndexNameService = optimizeIndexNameService;
    this.elasticSearchSchemaManager = elasticSearchSchemaManager;
  }

  @Bean(destroyMethod = "close")
  public OptimizeElasticsearchClient optimizeElasticsearchClient(
      final BackoffCalculator backoffCalculator) {
    return createOptimizeElasticsearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeElasticsearchClient createOptimizeElasticsearchClient(
      final BackoffCalculator backoffCalculator) {
    return OptimizeElasticsearchClientFactory.create(
        configurationService,
        optimizeIndexNameService,
        elasticSearchSchemaManager,
        backoffCalculator);
  }
}
