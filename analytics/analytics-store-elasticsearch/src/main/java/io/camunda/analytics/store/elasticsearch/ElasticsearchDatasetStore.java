/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;

/**
 * The Elasticsearch backend of the serving store: an {@link ElasticsearchClient} built from a
 * {@link ConnectConfiguration} via the shared {@link ElasticsearchConnector} (auth/TLS/plugins
 * reused, not reimplemented). Bundles the three neutral seams so the wiring layer selects it for
 * {@code DatabaseType.ELASTICSEARCH} and hands it to the pipeline.
 */
public final class ElasticsearchDatasetStore implements DatasetStore {

  private final ElasticsearchClient client;

  public ElasticsearchDatasetStore(final ConnectConfiguration configuration) {
    this(new ElasticsearchConnector(configuration).createClient());
  }

  public ElasticsearchDatasetStore(final ElasticsearchClient client) {
    this.client = client;
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    return new ElasticsearchDatasetSchemaManager(client);
  }

  @Override
  public DatasetWriter writer() {
    return new ElasticsearchDatasetWriter(client);
  }

  @Override
  public DatasetQueryClient queryClient() {
    return new ElasticsearchDatasetQueryClient(client);
  }

  @Override
  public void close() {
    try {
      client.close();
    } catch (final IOException e) {
      throw new IllegalStateException("failed to close the Elasticsearch client", e);
    }
  }
}
