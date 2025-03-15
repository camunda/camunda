/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import io.camunda.application.commons.migration.SchemaManagerHelper;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.schema.configuration.SearchEngineConfiguration;
import io.camunda.search.schema.exceptions.SearchEngineSchemaException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

public class SearchSchemaManagerConfiguration implements InitializingBean {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SearchSchemaManagerConfiguration.class);

  private final SearchEngineConfiguration searchEngineConfiguration;
  private final boolean createSchema;

  public SearchSchemaManagerConfiguration(
      final SearchEngineConfiguration searchEngineConfiguration,
      @Value("${camunda.database.create-schema:true}") final boolean createSchema) {
    this.searchEngineConfiguration = searchEngineConfiguration;
    this.createSchema = createSchema;
  }

  @Override
  public void afterPropertiesSet() throws IOException {
    if (createSchema) {
      try (final ClientAdapter clientAdapter =
          ClientAdapter.of(searchEngineConfiguration.connect())) {
        SchemaManagerHelper.createSchema(searchEngineConfiguration, clientAdapter);
      } catch (final SearchEngineSchemaException e) {
        if (e.getCause() instanceof java.net.ConnectException) {
          LOGGER.warn("Could not connect to search engine, skipping schema creation"); // FIXME
        } else {
          throw e;
        }
      }
    }
  }
}
