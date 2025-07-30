/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.it;

import static io.camunda.migration.process.adapter.Adapter.PROCESSOR_STEP_ID;
import static io.camunda.migration.process.adapter.Adapter.STEP_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.migration.process.ProcessMigrator;
import io.camunda.migration.process.TestData;
import io.camunda.migration.process.adapter.MigrationRepositoryIndex;
import io.camunda.migration.process.adapter.ProcessorStep;
import io.camunda.migration.process.config.ProcessMigrationProperties;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.schema.config.IndexConfiguration;
import io.camunda.search.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.search.schema.opensearch.OpensearchEngineClient;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class AdapterTest {
  protected static final String MISCONFIGURED_PREFIX = "misconfigured";
  protected static ProcessIndex processIndex;
  protected static MigrationRepositoryIndex migrationRepositoryIndex;
  protected static ImportPositionIndex importPositionIndex;
  protected static TestData.MisconfiguredProcessIndex misconfiguredProcessIndex;
  protected static ProcessMigrationProperties properties;
  protected static ElasticsearchClient esClient;
  protected static OpenSearchClient osClient;
  protected static final ConnectConfiguration ES_CONFIGURATION = new ConnectConfiguration();
  protected static final ConnectConfiguration OS_CONFIGURATION = new ConnectConfiguration();
  protected static ProcessMigrator osMigrator;
  protected static ProcessMigrator esMigrator;

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

  @BeforeAll
  public static void configure() {
    properties = new ProcessMigrationProperties();
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
    esMigrator = new ProcessMigrator(properties, ES_CONFIGURATION, meterRegistry);
    osMigrator = new ProcessMigrator(properties, OS_CONFIGURATION, meterRegistry);
    createIndices();
  }

  private static void createIndices() {
    final OpensearchEngineClient osEngine = new OpensearchEngineClient(osClient, osObjectMapper);
    processIndex = new ProcessIndex(ES_CONFIGURATION.getIndexPrefix(), false);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(ES_CONFIGURATION.getIndexPrefix(), false);
    importPositionIndex = new ImportPositionIndex(ES_CONFIGURATION.getIndexPrefix(), false);
    misconfiguredProcessIndex = new TestData.MisconfiguredProcessIndex(MISCONFIGURED_PREFIX, false);
    osEngine.createIndex(processIndex, new IndexConfiguration());
    osEngine.createIndex(migrationRepositoryIndex, new IndexConfiguration());
    osEngine.createIndex(importPositionIndex, new IndexConfiguration());
    osEngine.createIndex(misconfiguredProcessIndex, new IndexConfiguration());

    final ElasticsearchEngineClient esEngine =
        new ElasticsearchEngineClient(esClient, esObjectMapper);
    processIndex = new ProcessIndex(ES_CONFIGURATION.getIndexPrefix(), true);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(ES_CONFIGURATION.getIndexPrefix(), true);
    importPositionIndex = new ImportPositionIndex(ES_CONFIGURATION.getIndexPrefix(), true);
    misconfiguredProcessIndex = new TestData.MisconfiguredProcessIndex(MISCONFIGURED_PREFIX, true);
    esEngine.createIndex(processIndex, new IndexConfiguration());
    esEngine.createIndex(migrationRepositoryIndex, new IndexConfiguration());
    esEngine.createIndex(importPositionIndex, new IndexConfiguration());
    esEngine.createIndex(misconfiguredProcessIndex, new IndexConfiguration());
  }

  @AfterEach
  public void cleanUp() throws IOException {
    properties.setBatchSize(5);
    properties.setTimeout(Duration.ofMinutes(10));
    if (isElasticsearch) {
      esMigrator = new ProcessMigrator(properties, ES_CONFIGURATION, meterRegistry);
      esClient.deleteByQuery(
          DeleteByQueryRequest.of(
              d ->
                  d.index(
                          processIndex.getFullQualifiedName(),
                          migrationRepositoryIndex.getFullQualifiedName(),
                          importPositionIndex.getFullQualifiedName(),
                          misconfiguredProcessIndex.getFullQualifiedName())
                      .conflicts(Conflicts.Proceed)
                      .query(q -> q.matchAll(m -> m))));
      esClient.indices().refresh();

    } else {
      osMigrator = new ProcessMigrator(properties, OS_CONFIGURATION, meterRegistry);
      osClient.deleteByQuery(
          org.opensearch.client.opensearch.core.DeleteByQueryRequest.of(
              d ->
                  d.index(
                          processIndex.getFullQualifiedName(),
                          migrationRepositoryIndex.getFullQualifiedName(),
                          importPositionIndex.getFullQualifiedName(),
                          misconfiguredProcessIndex.getFullQualifiedName())
                      .conflicts(org.opensearch.client.opensearch._types.Conflicts.Proceed)
                      .query(q -> q.matchAll(m -> m))));
      osClient.indices().refresh();
    }
  }

  protected void runMigration() {
    if (isElasticsearch) {
      esMigrator.call();
    } else {
      osMigrator.call();
    }
  }

  protected void refreshIndices() throws IOException {
    if (isElasticsearch) {
      esClient.indices().refresh();
    } else {
      osClient.indices().refresh();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeToMisconfiguredProcessToIndex(final ProcessEntity entity) throws IOException {
    final var document =
        Map.of(
            "id",
            entity.getId(),
            "key",
            entity.getKey(),
            "bpmnXml",
            entity.getBpmnXml(),
            "version",
            entity.getVersion(),
            "bpmnProcessId",
            entity.getBpmnProcessId());
    if (isElasticsearch) {
      esClient.index(
          new co.elastic.clients.elasticsearch.core.IndexRequest.Builder()
              .index(misconfiguredProcessIndex.getFullQualifiedName())
              .document(document)
              .id(entity.getId())
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(misconfiguredProcessIndex.getFullQualifiedName())
              .document(document)
              .id(entity.getId())
              .build());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeProcessToIndex(final ProcessEntity entity) throws IOException {
    if (isElasticsearch) {
      esClient.index(
          new co.elastic.clients.elasticsearch.core.IndexRequest.Builder()
              .index(processIndex.getFullQualifiedName())
              .document(entity)
              .id(entity.getId())
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(processIndex.getFullQualifiedName())
              .document(entity)
              .id(entity.getId())
              .build());
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void writeProcessorStepToIndex(final String processDefinitionId) throws IOException {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(processDefinitionId);
    step.setApplied(true);
    step.setIndexName(ProcessIndex.INDEX_NAME);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    if (isElasticsearch) {
      esClient.index(
          new IndexRequest.Builder()
              .index(migrationRepositoryIndex.getFullQualifiedName())
              .document(step)
              .id(PROCESSOR_STEP_ID)
              .refresh(Refresh.True)
              .build());
    } else {
      osClient.index(
          new org.opensearch.client.opensearch.core.IndexRequest.Builder<>()
              .index(migrationRepositoryIndex.getFullQualifiedName())
              .document(step)
              .id(PROCESSOR_STEP_ID)
              .refresh(org.opensearch.client.opensearch._types.Refresh.True)
              .build());
    }
  }

  protected void writeImportPositionToIndex(final ImportPositionEntity... importPositionEntities)
      throws IOException {
    if (isElasticsearch) {
      final var req = new BulkRequest.Builder().refresh(Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op ->
                          op.index(
                              e ->
                                  e.id(imp.getId())
                                      .document(imp)
                                      .index(importPositionIndex.getFullQualifiedName()))));

      esClient.bulk(req.build());
    } else {
      final var req =
          new org.opensearch.client.opensearch.core.BulkRequest.Builder()
              .refresh(org.opensearch.client.opensearch._types.Refresh.True);
      Arrays.stream(importPositionEntities)
          .forEach(
              imp ->
                  req.operations(
                      op ->
                          op.index(
                              e ->
                                  e.id(imp.getId())
                                      .document(imp)
                                      .index(importPositionIndex.getFullQualifiedName()))));

      osClient.bulk(req.build());
    }
  }

  protected <T> List<T> readRecords(final Class<T> clazz, final String indexName)
      throws IOException {
    if (isElasticsearch) {
      final SearchRequest.Builder searchRequest =
          new SearchRequest.Builder().index(indexName).size(30).query(q -> q.matchAll(m -> m));
      final SearchResponse<T> searchResponse;

      searchResponse = esClient.search(searchRequest.build(), clazz);
      return searchResponse.hits().hits().stream().map(Hit::source).toList();
    } else {
      final org.opensearch.client.opensearch.core.SearchRequest.Builder searchRequest =
          new org.opensearch.client.opensearch.core.SearchRequest.Builder()
              .index(indexName)
              .size(30)
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
        .atMost(Duration.ofSeconds(5))
        .until(() -> readRecords(clazz, indexName).size() == recordCount);
  }

  protected void assertProcessorStepContentIsStored(final String processDefinitionId)
      throws IOException {
    final var records =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(1);
    assertThat(records.getFirst().getContent()).isEqualTo(String.valueOf(processDefinitionId));
  }
}
