/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.config.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.io.IOException;

public final class CamundaExporterSchemaUtils {
  private CamundaExporterSchemaUtils() {}

  public static void createSchemas(final ExporterConfiguration config) throws IOException {
    final var indexDescriptors =
        new IndexDescriptors(
            config.getConnect().getIndexPrefix(),
            config.getConnect().getTypeEnum().isElasticSearch());
    try (final ClientAdapter clientAdapter = ClientAdapter.of(config.getConnect())) {
      new SchemaManager(
              clientAdapter.getSearchEngineClient(),
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              SearchEngineConfiguration.of(
                  b ->
                      b.connect(config.getConnect())
                          .index(config.getIndex())
                          .retention(config.getHistory().getRetention())),
              clientAdapter.objectMapper())
          .startup();
    }
  }
}
