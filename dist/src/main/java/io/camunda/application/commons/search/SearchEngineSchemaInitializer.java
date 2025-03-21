/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

public class SearchEngineSchemaInitializer implements InitializingBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineSchemaInitializer.class);
  private final SearchEngineConfiguration searchEngineConfiguration;

  public SearchEngineSchemaInitializer(final SearchEngineConfiguration searchEngineConfiguration) {
    this.searchEngineConfiguration = searchEngineConfiguration;
  }

  @Override
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
              clientAdapter.objectMapper());
      schemaManager.startup();
    }
    LOGGER.info("Search engine schema initialization complete.");
  }
}
