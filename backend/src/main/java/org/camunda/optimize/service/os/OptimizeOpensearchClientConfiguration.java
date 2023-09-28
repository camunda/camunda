/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.plugin.OpensearchCustomHeaderProvider;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
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
public class OptimizeOpensearchClientConfiguration {
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  // private final OpenSearchSchemaManager openSearchSchemaManager; // TODO re-add with OPT-7229
  private final OpensearchCustomHeaderProvider opensearchCustomHeaderProvider;

  @Bean(destroyMethod = "close")
  public OptimizeOpensearchClient optimizeOpensearchClient(final BackoffCalculator backoffCalculator) {
    return createOptimizeOpensearchClient(backoffCalculator);
  }

  @SneakyThrows
  public OptimizeOpensearchClient createOptimizeOpensearchClient(final BackoffCalculator backoffCalculator) {
    return OptimizeOpensearchClientFactory.create(
      configurationService, optimizeIndexNameService,
      opensearchCustomHeaderProvider, backoffCalculator
    );
  }

}
