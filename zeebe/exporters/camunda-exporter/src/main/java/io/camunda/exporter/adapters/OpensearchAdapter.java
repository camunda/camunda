/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.OpenSearchFormCacheLoader;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.cache.process.OpenSearchProcessCacheLoader;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.OpensearchBatchRequest;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;

class OpensearchAdapter implements ClientAdapter {
  private final OpenSearchClient client;
  private final OpensearchEngineClient searchEngineClient;
  private final OpensearchExporterEntityCacheProvider entityCacheLoader;
  private final ObjectMapper objectMapper;

  OpensearchAdapter(final ConnectConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration);
    client = connector.createClient();
    objectMapper = connector.objectMapper();
    searchEngineClient = new OpensearchEngineClient(client, objectMapper);
    entityCacheLoader = new OpensearchExporterEntityCacheProvider(client);
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
    return new OpensearchBatchRequest(client, new BulkRequest.Builder());
  }

  @Override
  public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
    return entityCacheLoader;
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  record OpensearchExporterEntityCacheProvider(OpenSearchClient client)
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName) {
      return new OpenSearchProcessCacheLoader(client, processIndexName);
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return new OpenSearchFormCacheLoader(client, formIndexName);
    }
  }
}
