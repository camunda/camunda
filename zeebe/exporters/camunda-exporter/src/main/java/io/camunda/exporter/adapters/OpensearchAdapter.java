/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.OpensearchBatchRequest;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;

public class OpensearchAdapter implements ClientAdapter {
  private OpenSearchClient client;

  @Override
  public void createClient(final ConnectConfiguration config) {
    final var connector = new OpensearchConnector(config);
    client = connector.createClient();
  }

  @Override
  public SearchEngineClient createSearchEngineClient() {
    // FIXME add search engine client implementation for opensearch and use it here
    return null;
  }

  @Override
  public SchemaManager createSchemaManager(
      final ExporterResourceProvider provider, final ExporterConfiguration configuration) {

    // FIXME add SchemaManager implementation for opensearch and use it here
    return null;
  }

  @Override
  public BatchRequest createBatchRequest() {
    return new OpensearchBatchRequest(
        client, new BulkRequest.Builder(), new OpensearchScriptBuilder());
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }
}
