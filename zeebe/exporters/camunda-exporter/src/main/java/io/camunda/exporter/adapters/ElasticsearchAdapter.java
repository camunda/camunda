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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.db.search.engine.config.ConnectConfiguration;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.ElasticSearchFormCacheLoader;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.cache.process.ElasticSearchProcessCacheLoader;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.search.connect.es.ElasticsearchConnector;
import java.io.IOException;

class ElasticsearchAdapter implements ClientAdapter {

  private final ElasticsearchClient client;
  private final ElasticsearchEngineClient searchEngineClient;
  private final ElasticsearchExporterEntityCacheProvider entityCacheLoader;
  private final ObjectMapper objectMapper;

  ElasticsearchAdapter(final ConnectConfiguration configuration) {
    final var connector = new ElasticsearchConnector(configuration);
    client = connector.createClient();
    objectMapper = connector.objectMapper();
    searchEngineClient = new ElasticsearchEngineClient(client, objectMapper);
    entityCacheLoader = new ElasticsearchExporterEntityCacheProvider(client);
  }

  @Override
  public ObjectMapper objectMapper() {
    return objectMapper;
  }

  @Override
  public SearchEngineClient getSearchEngineClient() {
    return searchEngineClient;
  }

  @Override
  public BatchRequest createBatchRequest() {
    return new ElasticsearchBatchRequest(client, new BulkRequest.Builder());
  }

  @Override
  public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
    return entityCacheLoader;
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  record ElasticsearchExporterEntityCacheProvider(ElasticsearchClient client)
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName) {
      return new ElasticSearchProcessCacheLoader(client, processIndexName);
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return new ElasticSearchFormCacheLoader(client, formIndexName);
    }
  }
}
