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
import io.camunda.exporter.schema.ElasticsearchEngineClient;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;

public class ElasticsearchAdapter implements ClientAdapter {
  private ElasticsearchClient client;

  @Override
  public void createClient(final ConnectConfiguration config) {
    final var connector = new ElasticsearchConnector(config);
    client = connector.createClient();
  }

  @Override
  public SearchEngineClient createSearchEngineClient() {
    return new ElasticsearchEngineClient(client);
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
