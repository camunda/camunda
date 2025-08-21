/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.migration.commons;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class MigrationTest {
  protected static MigrationConfiguration properties;
  protected static ElasticsearchClient esClient;
  protected static OpenSearchClient osClient;
  protected static final ConnectConfiguration ES_CONFIGURATION = new ConnectConfiguration();
  protected static final ConnectConfiguration OS_CONFIGURATION = new ConnectConfiguration();
  protected static MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Container
  private static final ElasticsearchContainer ES_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final OpensearchContainer<?> OS_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static ObjectMapper esObjectMapper;
  private static ObjectMapper osObjectMapper;
  protected boolean isElasticsearch = true;

  protected <T extends IndexDescriptor> String indexFqnForClass(final Class<T> clazz) {
    final var prefix =
        isElasticsearch ? ES_CONFIGURATION.getIndexPrefix() : OS_CONFIGURATION.getIndexPrefix();
    try {
      return clazz
          .getConstructor(String.class, boolean.class)
          .newInstance(prefix, isElasticsearch)
          .getFullQualifiedName();
    } catch (final NoSuchMethodException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  protected abstract Migrator supplyMigrator(
      ConnectConfiguration connectConfiguration,
      MigrationConfiguration migrationConfiguration,
      MeterRegistry meterRegistry);

  protected abstract IndexDescriptor[] requiredIndices(String prefix, boolean isElasticsearch);

  private void createIndices() {
    final var idxConfiguration = new IndexConfiguration();
    final OpensearchEngineClient osEngine = new OpensearchEngineClient(osClient, osObjectMapper);
    final ElasticsearchEngineClient esEngine =
        new ElasticsearchEngineClient(esClient, esObjectMapper);
    final var prefix =
        isElasticsearch ? ES_CONFIGURATION.getIndexPrefix() : OS_CONFIGURATION.getIndexPrefix();
    Arrays.stream(requiredIndices(prefix, isElasticsearch))
        .forEach(
            idx -> {
              osEngine.createIndex(idx, idxConfiguration);
              esEngine.createIndex(idx, idxConfiguration);
            });
  }

  protected void refreshIndices() throws IOException {
    if (isElasticsearch) {
      esClient.indices().refresh();
    } else {
      osClient.indices().refresh();
    }
  }

  @BeforeEach
  public void setup() {
    properties = new MigrationConfiguration();
    properties.setBatchSize(5);
    properties.getRetry().setMinRetryDelay(Duration.ofMillis(100));
    properties.getRetry().setMaxRetryDelay(Duration.ofMillis(500));
    properties.setImporterFinishedTimeout(Duration.ofSeconds(1));
    ES_CONFIGURATION.setUrl("http://localhost:" + ES_CONTAINER.getMappedPort(9200));
    OS_CONFIGURATION.setType("opensearch");
    OS_CONFIGURATION.setUrl("http://localhost:" + OS_CONTAINER.getMappedPort(9200));
    final var esConnector = new ElasticsearchConnector(ES_CONFIGURATION);
    esObjectMapper = esConnector.objectMapper();
    esClient = esConnector.createClient();
    final var osConnector = new OpensearchConnector(OS_CONFIGURATION);
    osObjectMapper = osConnector.objectMapper();
    osClient = osConnector.createClient();
    createIndices();
  }

  @AfterEach
  public void tearDown() throws IOException {
    final var prefix =
        isElasticsearch ? ES_CONFIGURATION.getIndexPrefix() : OS_CONFIGURATION.getIndexPrefix();
    final var indices =
        Arrays.stream(requiredIndices(prefix, isElasticsearch))
            .map(IndexDescriptor::getFullQualifiedName)
            .toList();
    if (isElasticsearch) {
      esClient.indices().delete(d -> d.index(indices));
    } else {
      osClient.indices().delete(d -> d.index(indices));
    }
    refreshIndices();
  }

  protected void runMigration() throws Exception {
    if (isElasticsearch) {
      supplyMigrator(ES_CONFIGURATION, properties, meterRegistry).call();
    } else {
      supplyMigrator(OS_CONFIGURATION, properties, meterRegistry).call();
    }
  }

  protected <T> List<T> readRecords(final Class<T> clazz, final String indexName)
      throws IOException {
    if (isElasticsearch) {
      final SearchRequest.Builder searchRequest =
          new SearchRequest.Builder().index(indexName).size(10000).query(q -> q.matchAll(m -> m));
      final SearchResponse<T> searchResponse;

      searchResponse = esClient.search(searchRequest.build(), clazz);
      return searchResponse.hits().hits().stream().map(Hit::source).toList();
    } else {
      final org.opensearch.client.opensearch.core.SearchRequest.Builder searchRequest =
          new org.opensearch.client.opensearch.core.SearchRequest.Builder()
              .index(indexName)
              .size(10000)
              .query(q -> q.matchAll(m -> m));
      final org.opensearch.client.opensearch.core.SearchResponse<T> searchResponse;

      searchResponse = osClient.search(searchRequest.build(), clazz);
      return searchResponse.hits().hits().stream()
          .map(org.opensearch.client.opensearch.core.search.Hit::source)
          .toList();
    }
  }

  protected <T> void awaitRecordsArePresent(
      final Class<T> clazz, final String indexName, final int recordCount) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> readRecords(clazz, indexName).size() == recordCount);
  }

  protected void writeImportPositionToIndex(
      final String indexName, final ImportPositionEntity... importPositionEntities)
      throws IOException {
    if (isElasticsearch) {
      final var req = new BulkRequest.Builder().refresh(Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op -> op.index(e -> e.id(imp.getId()).document(imp).index(indexName))));

      esClient.bulk(req.build());
    } else {
      final var req =
          new org.opensearch.client.opensearch.core.BulkRequest.Builder()
              .refresh(org.opensearch.client.opensearch._types.Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op -> op.index(e -> e.id(imp.getId()).document(imp).index(indexName))));

      osClient.bulk(req.build());
    }
  }
}
