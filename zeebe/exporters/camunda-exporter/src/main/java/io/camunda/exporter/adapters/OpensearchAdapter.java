/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.OpenSearchFormCacheLoader;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.cache.process.OpenSearchProcessCacheLoader;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.schema.SearchEngineClient;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.OpensearchBatchRequest;
import io.camunda.exporter.utils.OpensearchScriptBuilder;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.search.connect.os.OpensearchConnector;
import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;

class OpensearchAdapter implements ClientAdapter {
  private final OpenSearchClient client;
  private final OpensearchEngineClient searchEngineClient;
  private final OpensearchExporterEntityCacheProvider entityCacheLoader;

  OpensearchAdapter(final ExporterConfiguration configuration) {
    final var connector = new OpensearchConnector(configuration.getConnect());
    client = connector.createClient();
    searchEngineClient = new OpensearchEngineClient(client);
    entityCacheLoader = new OpensearchExporterEntityCacheProvider(client);
  }

  @Override
  public SearchEngineClient getSearchEngineClient() {
    return searchEngineClient;
  }

  @Override
  public BatchRequest createBatchRequest() {
    return new OpensearchBatchRequest(
        client, new BulkRequest.Builder(), new OpensearchScriptBuilder());
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
        final String processIndexName, final XMLUtil xmlUtil) {
      return new OpenSearchProcessCacheLoader(client, processIndexName, xmlUtil);
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return new OpenSearchFormCacheLoader(client, formIndexName);
    }
  }
}
