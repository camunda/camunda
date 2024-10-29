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
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.CachedProcessEntity;
import io.camunda.exporter.cache.ElasticSearchProcessCacheLoader;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;

class ElasticsearchAdapter implements ClientAdapter {
  private final ElasticsearchClient client;
  private final ElasticsearchEngineClient searchEngineClient;

  ElasticsearchAdapter(final ExporterConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration.getConnect());
    client = connector.createClient();
    searchEngineClient = new ElasticsearchEngineClient(client);
  }

  @Override
  public SearchEngineClient getSearchEngineClient() {
    return searchEngineClient;
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

  @Override
  public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
      final String processIndexName) {
    return new ElasticSearchProcessCacheLoader(client, processIndexName);
  }
}
