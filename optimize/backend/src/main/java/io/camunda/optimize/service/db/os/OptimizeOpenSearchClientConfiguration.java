/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
@Slf4j
@Conditional(OpenSearchCondition.class)
public class OptimizeOpenSearchClientConfiguration {
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final OpenSearchSchemaManager openSearchSchemaManager;

  @Bean(destroyMethod = "close")
  public OptimizeOpenSearchClient optimizeOpenSearchClient(
      final BackoffCalculator backoffCalculator) {
    return createOptimizeOpenSearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeOpenSearchClient createOptimizeOpenSearchClient(
      final BackoffCalculator backoffCalculator) {
    return OptimizeOpenSearchClientFactory.create(
        configurationService, optimizeIndexNameService, openSearchSchemaManager, backoffCalculator);
  }
}
