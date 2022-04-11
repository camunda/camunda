/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
public class OptimizeElasticsearchClientConfiguration {
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final ElasticSearchSchemaManager elasticSearchSchemaManager;
  private final ElasticsearchCustomHeaderProvider elasticsearchCustomHeaderProvider;

  @Bean(destroyMethod = "close")
  public OptimizeElasticsearchClient optimizeElasticsearchClient(final BackoffCalculator backoffCalculator) {
    return createOptimizeElasticsearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeElasticsearchClient createOptimizeElasticsearchClient(final BackoffCalculator backoffCalculator) {
    return OptimizeElasticsearchClientFactory.create(
      configurationService, optimizeIndexNameService, elasticSearchSchemaManager,
      elasticsearchCustomHeaderProvider, backoffCalculator
    );
  }

}
