/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os;

import io.camunda.optimize.service.db.os.schema.OpenSearchSchemaManager;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.search.connect.plugin.PluginRepository;
import java.io.IOException;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(OpenSearchCondition.class)
public class OptimizeOpenSearchClientConfiguration {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(OptimizeOpenSearchClientConfiguration.class);
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService optimizeIndexNameService;
  private final OpenSearchSchemaManager openSearchSchemaManager;
  private final PluginRepository pluginRepository = new PluginRepository();

  public OptimizeOpenSearchClientConfiguration(
      final ConfigurationService configurationService,
      final OptimizeIndexNameService optimizeIndexNameService,
      final OpenSearchSchemaManager openSearchSchemaManager) {
    this.configurationService = configurationService;
    this.optimizeIndexNameService = optimizeIndexNameService;
    this.openSearchSchemaManager = openSearchSchemaManager;
  }

  @Bean(destroyMethod = "close")
  public OptimizeOpenSearchClient optimizeOpenSearchClient(
      final BackoffCalculator backoffCalculator) {
    return createOptimizeOpenSearchClient(backoffCalculator);
  }

  public OptimizeOpenSearchClient createOptimizeOpenSearchClient(
      final BackoffCalculator backoffCalculator) {
    try {
      return OptimizeOpenSearchClientFactory.create(
          configurationService,
          optimizeIndexNameService,
          openSearchSchemaManager,
          backoffCalculator,
          pluginRepository);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }
  }
}
