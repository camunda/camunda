/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.ElasticsearchEngineClient;
import io.camunda.exporter.schema.ElasticsearchSchemaManager;
import io.camunda.exporter.schema.SchemaManager;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;
import java.util.Objects;

public class ElasticsearchAdapter implements ClientAdapter {
  private ElasticsearchClient client;
  private ElasticsearchEngineClient searchEngineClient;

  @Override
  public void createClient(final ConnectConfiguration config) {
    final var connector = new ElasticsearchConnector(config);
    client = connector.createClient();
  }

  @Override
  public SearchEngineClient createSearchEngineClient() {
    Objects.requireNonNull(
        client, "ElasticsearchClient must be created before creating search engine client");
    searchEngineClient = new ElasticsearchEngineClient(client);
    return searchEngineClient;
  }

  @Override
  public SchemaManager createSchemaManager(
      final ExporterResourceProvider provider, final ExporterConfiguration configuration) {
    if (searchEngineClient == null) {
      createSearchEngineClient();
    }

    return new ElasticsearchSchemaManager(
        searchEngineClient,
        provider.getIndexDescriptors(),
        provider.getIndexTemplateDescriptors(),
        configuration);
  }

  @Override
  public BatchRequest createBatchRequest() {
    Objects.requireNonNull(
        client, "ElasticsearchClient must be created before creating batch request");
    return new ElasticsearchBatchRequest(
        client, new BulkRequest.Builder(), new ElasticsearchScriptBuilder());
  }

  @Override
  public void close() throws IOException {
    Objects.requireNonNull(client, "No ElasticsearchClient instance to close");
    client._transport().close();
  }
}
