/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.search.schema.metrics.SchemaManagerMetrics;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchEngineSchemaInitializer {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineSchemaInitializer.class);
  private final SearchEngineConfiguration searchEngineConfiguration;
  private final SchemaManagerMetrics schemaManagerMetrics;

  public SearchEngineSchemaInitializer(
      final SearchEngineConfiguration searchEngineConfiguration,
      final MeterRegistry meterRegistry) {
    this.searchEngineConfiguration = searchEngineConfiguration;
    schemaManagerMetrics = new SchemaManagerMetrics(meterRegistry);
  }

  public void afterPropertiesSet() throws IOException {
    LOGGER.info("Initializing search engine schema...");
    try (final var clientAdapter = ClientAdapter.of(searchEngineConfiguration.connect())) {
      final IndexDescriptors indexDescriptors =
          new IndexDescriptors(
              searchEngineConfiguration.connect().getIndexPrefix(),
              searchEngineConfiguration.connect().getTypeEnum().isElasticSearch());
      final SchemaManager schemaManager =
          new SchemaManager(
                  clientAdapter.getSearchEngineClient(),
                  indexDescriptors.indices(),
                  indexDescriptors.templates(),
                  searchEngineConfiguration,
                  clientAdapter.objectMapper())
              .withMetrics(schemaManagerMetrics);
      schemaManager.startup();
    }
    LOGGER.info("Search engine schema initialization complete.");
  }
}
