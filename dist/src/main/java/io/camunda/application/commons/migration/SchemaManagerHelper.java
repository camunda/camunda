/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.schema.SchemaManager;
import io.camunda.search.schema.configuration.SearchEngineConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SchemaManagerHelper {
  private static final Logger LOG = LoggerFactory.getLogger(SchemaManagerHelper.class);

  private SchemaManagerHelper() {}

  public static void createSchema(
      final SearchEngineConfiguration searchEngineConfiguration,
      final ClientAdapter clientAdapter) {
    final var isElasticsearch = searchEngineConfiguration.connect().getTypeEnum().isElasticSearch();
    final IndexDescriptors indexDescriptors =
        new IndexDescriptors(searchEngineConfiguration.connect().getIndexPrefix(), isElasticsearch);

    final SchemaManager schemaManager =
        new SchemaManager(
            clientAdapter.getSearchEngineClient(),
            indexDescriptors.indices(),
            indexDescriptors.templates(),
            searchEngineConfiguration,
            clientAdapter.objectMapper());

    schemaManager.startup();
  }

  public static ClientAdapter createClientAdapter(final ConnectConfiguration connectConfig) {
    return ClientAdapter.of(connectConfig);
  }
}
