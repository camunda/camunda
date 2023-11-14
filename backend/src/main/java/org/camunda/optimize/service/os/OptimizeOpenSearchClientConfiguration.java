/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.OpenSearchCustomHeaderProvider;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.os.schema.OpenSearchSchemaManager;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
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
  private final OpenSearchCustomHeaderProvider opensearchCustomHeaderProvider;

  @Bean(destroyMethod = "close")
  public OptimizeOpenSearchClient optimizeOpenSearchClient(final BackoffCalculator backoffCalculator) {
    return createOptimizeOpenSearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeOpenSearchClient createOptimizeOpenSearchClient(final BackoffCalculator backoffCalculator) {
    return OptimizeOpenSearchClientFactory.create(
      configurationService, optimizeIndexNameService, openSearchSchemaManager,
      opensearchCustomHeaderProvider, backoffCalculator
    );
  }

}
