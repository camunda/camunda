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
import io.camunda.search.test.utils.SearchDBExtension;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.awaitility.Awaitility;

public final class CamundaExporterSchemaUtils {

  /**
   * SchemaManager.startupOnce() is a single attempt by design; on prod we retry schema creation
   * with backoff so we can complete initialization in a loaded managed cluster taking more than one
   * attempt. Similarly, we will retry schema creation in tests to avoid flakiness and the timeout
   * will control how/long we would retry.
   */
  private static final Duration SCHEMA_CREATION_TIMEOUT =
      Optional.ofNullable(
              System.getProperty(SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_TIMEOUT))
          .map(val -> Duration.ofSeconds(Long.parseLong(val)))
          .orElse(Duration.ofSeconds(120));

  private CamundaExporterSchemaUtils() {}

  public static void createSchemas(final ExporterConfiguration config) throws IOException {
    final var indexDescriptors =
        new IndexDescriptors(
            config.getConnect().getIndexPrefix(),
            config.getConnect().getTypeEnum().isElasticSearch());
    try (final ClientAdapter clientAdapter = ClientAdapter.of(config.getConnect())) {
      final var schemaManager =
          new SchemaManager(
              clientAdapter.getSearchEngineClient(),
              indexDescriptors.indices(),
              indexDescriptors.templates(),
              SearchEngineConfiguration.of(
                  b ->
                      b.connect(config.getConnect())
                          .index(config.getIndex())
                          .retention(config.getHistory().getRetention())),
              clientAdapter.objectMapper());

      Awaitility.await("schema manager startup & initialization completes")
          .ignoreExceptions()
          .atMost(SCHEMA_CREATION_TIMEOUT)
          .until(
              () -> {
                schemaManager.startupOnce();
                return true;
              });
    }
  }
}
