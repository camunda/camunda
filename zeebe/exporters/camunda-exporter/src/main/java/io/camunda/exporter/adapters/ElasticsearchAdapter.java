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
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;

class ElasticsearchAdapter implements ClientAdapter {
  private final ExporterConfiguration configuration;
  private final ElasticsearchClient client;
  private final ElasticsearchEngineClient searchEngineClient;

  ElasticsearchAdapter(final ExporterConfiguration configuration) {
    this.configuration = configuration;
    final var connector = new ElasticsearchConnector(configuration.getConnect());
    client = connector.createClient();
    searchEngineClient = new ElasticsearchEngineClient(client);
  }

  @Override
  public SearchEngineClient getSearchEngineClient() {
    return searchEngineClient;
  }

  @Override
  public SchemaManager createSchemaManager(final ExporterResourceProvider provider) {
    return new ElasticsearchSchemaManager(
        searchEngineClient,
        provider.getIndexDescriptors(),
        provider.getIndexTemplateDescriptors(),
        configuration);
  }

  @Override
  public BatchRequest createBatchRequest() {
    return new ElasticsearchBatchRequest(
        client, new BulkRequest.Builder(), new ElasticsearchScriptBuilder());
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }
}
