/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.ElasticsearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@Slf4j
public class OptimizeElasticsearchClientConfiguration {

  @Bean(destroyMethod = "close")
  public OptimizeElasticsearchClient optimizeElasticsearchClient(
    final ConfigurationService configurationService,
    final OptimizeIndexNameService optimizeIndexNameService,
    final ElasticSearchSchemaManager elasticSearchSchemaManager,
    final ElasticsearchCustomHeaderProvider elasticsearchCustomHeaderProvider,
    final BackoffCalculator backoffCalculator) throws IOException {

    return OptimizeElasticsearchClientFactory.create(
      configurationService, optimizeIndexNameService, elasticSearchSchemaManager,
      elasticsearchCustomHeaderProvider, backoffCalculator
    );

  }

}
