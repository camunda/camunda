/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.adapters;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.batchoperation.ElasticSearchBatchOperationCacheLoader;
import io.camunda.exporter.cache.decision.ElasticSearchDecisionRequirementsCacheLoader;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.form.ElasticSearchFormCacheLoader;
import io.camunda.exporter.cache.process.ElasticSearchProcessCacheLoader;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.store.ElasticsearchBatchRequest;
import io.camunda.exporter.utils.ElasticsearchScriptBuilder;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.decisionRequirements.CachedDecisionRequirementsEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import java.io.IOException;

class ElasticsearchAdapter implements ClientAdapter {
  private final ElasticsearchClient client;
  private final ElasticsearchEngineClient searchEngineClient;
  private final ElasticsearchExporterEntityCacheProvider entityCacheLoader;
  private final ObjectMapper objectMapper;
  private final boolean ownsClient;

  ElasticsearchAdapter(final ConnectConfiguration configuration) {
    this(new ElasticsearchConnector(configuration));
  }

  private ElasticsearchAdapter(final ElasticsearchConnector connector) {
    this(connector.createClient(), connector.objectMapper(), true);
  }

  /** Wraps a client owned by someone else; {@link #close()} leaves it open. */
  ElasticsearchAdapter(final ElasticsearchClient client) {
    this(client, objectMapperOf(client), false);
  }

  private ElasticsearchAdapter(
      final ElasticsearchClient client, final ObjectMapper objectMapper, final boolean ownsClient) {
    this.client = client;
    this.objectMapper = objectMapper;
    this.ownsClient = ownsClient;
    searchEngineClient = new ElasticsearchEngineClient(client, objectMapper);
    entityCacheLoader = new ElasticsearchExporterEntityCacheProvider(client);
  }

  private static ObjectMapper objectMapperOf(final ElasticsearchClient client) {
    if (client._jsonpMapper() instanceof final JacksonJsonpMapper jackson) {
      return jackson.objectMapper();
    }
    throw new IllegalArgumentException(
        "Expected a client using JacksonJsonpMapper, but got " + client._jsonpMapper());
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
    return new ElasticsearchBatchRequest(client, new ElasticsearchScriptBuilder());
  }

  @Override
  public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
    return entityCacheLoader;
  }

  @Override
  public void close() throws IOException {
    if (ownsClient) {
      client.close();
    }
  }

  record ElasticsearchExporterEntityCacheProvider(ElasticsearchClient client)
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<String, CachedBatchOperationEntity> getBatchOperationCacheLoader(
        final String batchOperationIndexName) {
      return new ElasticSearchBatchOperationCacheLoader(client, batchOperationIndexName);
    }

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName,
        final ExtensionPropertyConfiguration extensionPropertiesConfiguration) {
      return new ElasticSearchProcessCacheLoader(
          client, processIndexName, extensionPropertiesConfiguration);
    }

    @Override
    public CacheLoader<Long, CachedDecisionRequirementsEntity> getDecisionRequirementsCacheLoader(
        final String decisionRequirementsIndexName) {
      return new ElasticSearchDecisionRequirementsCacheLoader(
          client, decisionRequirementsIndexName);
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return new ElasticSearchFormCacheLoader(client, formIndexName);
    }
  }
}
