/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.opensearch;

import io.camunda.analytics.dataset.store.DatasetQueryClient;
import io.camunda.analytics.dataset.store.DatasetSchemaManager;
import io.camunda.analytics.dataset.store.DatasetStore;
import io.camunda.analytics.dataset.store.DatasetWriter;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;

/**
 * The OpenSearch backend of the serving store: an {@link OpenSearchClient} built from a {@link
 * ConnectConfiguration} via the shared {@link OpensearchConnector} (auth/TLS/plugins reused, not
 * reimplemented). Bundles the three neutral seams so the wiring layer selects it for {@code
 * DatabaseType.OPENSEARCH} and hands it to the pipeline.
 */
public final class OpensearchDatasetStore implements DatasetStore {

  private final OpenSearchClient client;

  public OpensearchDatasetStore(final ConnectConfiguration configuration) {
    this(new OpensearchConnector(configuration).createClient());
  }

  public OpensearchDatasetStore(final OpenSearchClient client) {
    this.client = client;
  }

  @Override
  public DatasetSchemaManager schemaManager() {
    return new OpensearchDatasetSchemaManager(client);
  }

  @Override
  public DatasetWriter writer() {
    return new OpensearchDatasetWriter(client);
  }

  @Override
  public DatasetQueryClient queryClient() {
    return new OpensearchDatasetQueryClient(client);
  }

  @Override
  public void close() {
    try {
      client._transport().close();
    } catch (final IOException e) {
      throw new IllegalStateException("failed to close the OpenSearch client", e);
    }
  }
}
