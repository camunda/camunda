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
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.OpensearchBatchRequest;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;

class OpensearchAdapter implements ClientAdapter {
  private final OpenSearchClient client;
  private final ExporterConfiguration configuration;
  private final OpensearchEngineClient searchEngineClient;

  OpensearchAdapter(final ExporterConfiguration configuration) {
    this.configuration = configuration;
    final var connector = new OpensearchConnector(configuration.getConnect());
    client = connector.createClient();
    searchEngineClient = new OpensearchEngineClient(client);
  }

  @Override
  public SearchEngineClient getSearchEngineClient() {
    return searchEngineClient;
  }

  @Override
  public SchemaManager createSchemaManager(final ExporterResourceProvider provider) {
    return new SchemaManager(
        searchEngineClient,
        provider.getIndexDescriptors(),
        provider.getIndexTemplateDescriptors(),
        configuration);
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
