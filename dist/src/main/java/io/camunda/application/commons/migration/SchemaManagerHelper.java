/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.db.DatabaseType;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SchemaManagerHelper {

  private static final Logger LOG = LoggerFactory.getLogger(SchemaManagerHelper.class);

  private SchemaManagerHelper() {
  }

  public static void createSchema(
      final ConnectConfiguration connectConfig, final ClientAdapter clientAdapter) {
    final var config = createExporterConfig(connectConfig);
    final var isElasticsearch = connectConfig.getTypeEnum() == DatabaseType.ELASTICSEARCH;

    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(connectConfig.getIndexPrefix(), isElasticsearch);

    final SchemaManager schemaManager =
        new SchemaManager(
            clientAdapter.getSearchEngineClient(),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            config,
            clientAdapter.objectMapper());

    schemaManager.startup();
  }

  public static ClientAdapter createClientAdapter(final ConnectConfiguration connectConfig) {
    final var config = createExporterConfig(connectConfig);
    return ClientAdapter.of(config);
  }

  private static ExporterConfiguration createExporterConfig(
      final ConnectConfiguration connectConfig) {
    final var config = new ExporterConfiguration();
    config.setConnect(connectConfig);
    config.getIndex().setPrefix(connectConfig.getIndexPrefix());

    return config;
  }
}
