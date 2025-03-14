/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.util.camunda.exporter;

import static io.camunda.application.commons.migration.SchemaManagerHelper.*;

import io.camunda.application.commons.migration.SchemaManagerHelper;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.configuration.IndexConfiguration;
import io.camunda.search.schema.configuration.SearchEngineConfiguration;
import java.io.IOException;

public class SchemaWithExporter {

  private final SearchEngineConfiguration config;

  public SchemaWithExporter(final String prefix, final boolean isElasticsearch) {
    final var indexSettings = new IndexConfiguration();
    indexSettings.setPrefix(prefix);
    final var connectConfig = new ConnectConfiguration();
    connectConfig.setType(
        isElasticsearch
            ? ConnectionTypes.ELASTICSEARCH.getType()
            : ConnectionTypes.OPENSEARCH.getType());
    config = SearchEngineConfiguration.of(b -> b.connect(connectConfig).index(indexSettings));
  }

  public void createSchema() {
    try (final var clientAdapter = createClientAdapter(config.connect())) {
      SchemaManagerHelper.createSchema(config, clientAdapter);
    } catch (final IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}
