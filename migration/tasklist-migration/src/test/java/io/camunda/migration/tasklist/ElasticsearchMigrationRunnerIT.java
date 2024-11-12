/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.tasklist;

import static io.camunda.migration.tasklist.adapter.Adapter.PROCESSOR_STEP_ID;
import static io.camunda.migration.tasklist.adapter.Adapter.STEP_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.elasticsearch.ElasticsearchEngineClient;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class ElasticsearchMigrationRunnerIT {

  @Container
  private static final ElasticsearchContainer ES_CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static ElasticsearchClient esClient;
  private static MigrationRunner migrator;
  private static TasklistMigrationProperties properties;
  private static ProcessIndex processIndex;
  private static MigrationRepositoryIndex migrationRepositoryIndex;

  @BeforeAll
  public static void setUp() throws IOException {
    properties = new TasklistMigrationProperties();
    properties.setBatchSize(5);
    final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setUrl("http://localhost:" + ES_CONTAINER.getMappedPort(9200));
    System.out.println(ES_CONTAINER.getMappedPort(9200));
    properties.setConnect(connectConfiguration);
    final var connector = new ElasticsearchConnector(properties.getConnect());
    esClient = connector.createClient();
    createIndices();
  }

  private static void createIndices() {
    final ElasticsearchEngineClient es = new ElasticsearchEngineClient(esClient);
    processIndex = new ProcessIndex(properties.getConnect().getIndexPrefix(), true);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(properties.getConnect().getIndexPrefix(), true);

    es.createIndex(processIndex, new IndexSettings());
    es.createIndex(migrationRepositoryIndex, new IndexSettings());
  }

  @BeforeEach
  public void cleanUp() throws IOException {
    properties.setBatchSize(5);
    migrator = new MigrationRunner(properties);
    esClient.deleteByQuery(
        DeleteByQueryRequest.of(
            d ->
                d.index(processIndex.getFullQualifiedName())
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.matchAll(m -> m))));
    esClient.deleteByQuery(
        DeleteByQueryRequest.of(
            d ->
                d.index(migrationRepositoryIndex.getFullQualifiedName())
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.matchAll(m -> m))));
    esClient.indices().refresh();
  }

  @Test
  public void singleMigrationRound() throws IOException {
    // when
    properties.setBatchSize(1);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithForm(1L);
    final ProcessEntity entityNotToBeMigrated = TestData.processEntityWithForm(2L);
    writeProcessToIndex(entityToBeMigrated);
    writeProcessToIndex(entityNotToBeMigrated);
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());

    // then
    migrator.migrateBatch(List.of(entityToBeMigrated));
    awaitRecordsArePresent(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    esClient.indices().refresh();
    // verify
    assertProcessorStepContentIsStored("1");
    final var processorRecords =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(processorRecords.size()).isEqualTo(1);
    assertThat(processorRecords.getFirst().getContent())
        .isEqualTo(String.valueOf(entityToBeMigrated.getKey()));

    final var processRecords =
        readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(processRecords.size()).isEqualTo(2);
    assertThat(processRecords.stream().filter(r -> r.getKey() == 1).findFirst().get().getIsPublic())
        .isTrue();
    assertThat(processRecords.stream().filter(r -> r.getKey() == 1).findFirst().get().getFormId())
        .isEqualTo("testForm");
    assertThat(processRecords.stream().filter(r -> r.getKey() == 2).findFirst().get().getIsPublic())
        .isNull();
    assertThat(processRecords.stream().filter(r -> r.getKey() == 2).findFirst().get().getFormId())
        .isNull();
  }

  @Test
  public void shouldMigrateSuccessfully() throws IOException {
    // when
    writeProcessToIndex(TestData.processEntityWithForm(1L));
    writeProcessToIndex(TestData.processEntityWithoutForm(2L));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    esClient.indices().refresh();
    // verify
    assertProcessorStepContentIsStored("2");

    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(2);
    assertThat(records.stream().filter(r -> r.getId().equals("1")).findFirst().get().getIsPublic())
        .isTrue();
    assertThat(records.stream().filter(r -> r.getId().equals("1")).findFirst().get().getFormId())
        .isEqualTo("testForm");
    assertThat(records.stream().filter(r -> r.getId().equals("2")).findFirst().get().getIsPublic())
        .isFalse();
    assertThat(records.stream().filter(r -> r.getId().equals("2")).findFirst().get().getFormId())
        .isNull();
  }

  @Test
  public void migrationShouldCompleteWithMultipleRounds() throws IOException {
    // when
    for (int i = 1; i <= 20; i++) {
      writeProcessToIndex(TestData.processEntityWithForm((long) i));
    }
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    esClient.indices().refresh();

    // verify
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    // Since the key field is marked as a keyword in ES/OS the sorting is done lexicographically
    assertProcessorStepContentIsStored("9");
    assertThat(records.size()).isEqualTo(20);
    assertThat(records.stream().noneMatch(r -> r.getIsPublic().equals(Boolean.FALSE))).isTrue();
    assertThat(records.stream().noneMatch(r -> r.getFormId() == null)).isTrue();
  }

  @Test
  public void migrationShouldPickUpFromStoredId() throws IOException {
    // when
    for (int i = 1; i <= 9; i++) {
      writeProcessToIndex(TestData.processEntityWithForm((long) i));
    }
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    writeProcessorStepToIndex("5");
    // then
    migrator.run();
    esClient.indices().refresh();

    // verify
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    // Since the key field is marked as a keyword in ES/OS the sorting is done lexicographically
    assertProcessorStepContentIsStored("9");
    assertThat(records.size()).isEqualTo(9);
    assertThat(records.stream().filter(r -> r.getKey() <= 5).allMatch(r -> r.getIsPublic() == null))
        .isTrue();
    assertThat(records.stream().filter(r -> r.getKey() <= 5).allMatch(r -> r.getFormId() == null))
        .isTrue();
    assertThat(
            records.stream()
                .filter(r -> r.getKey() > 5)
                .allMatch(r -> r.getIsPublic().equals(Boolean.TRUE)))
        .isTrue();
    assertThat(
            records.stream()
                .filter(r -> r.getKey() > 5)
                .allMatch(r -> r.getFormId().equals("testForm")))
        .isTrue();
  }

  @Test
  public void migrationShouldDoNothingWhenFinalStepIsPresent() throws IOException {
    // when
    properties.setBatchSize(1);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithForm(1L);
    final ProcessEntity entityNotToBeMigrated = TestData.processEntityWithForm(2L);
    writeProcessorStepToIndex("2");
    writeProcessToIndex(entityToBeMigrated);
    writeProcessToIndex(entityNotToBeMigrated);
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    esClient.indices().refresh();

    // verify
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    final var stepRecords =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(2);
    assertThat(records.stream().allMatch(r -> r.getIsPublic() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormId() == null)).isTrue();
    assertThat(stepRecords.size()).isEqualTo(1);
    assertThat(stepRecords.getFirst().getContent()).isEqualTo("2");
  }

  private void writeProcessToIndex(final ProcessEntity entity) throws IOException {
    esClient.index(
        new IndexRequest.Builder<>()
            .index(processIndex.getFullQualifiedName())
            .document(entity)
            .id(entity.getId())
            .build());
  }

  private void writeProcessorStepToIndex(final String processDefinitionId) throws IOException {
    final ProcessorStep step = new ProcessorStep();
    step.setContent(processDefinitionId);
    step.setApplied(true);
    step.setIndexName(ProcessIndex.INDEX_NAME);
    step.setDescription(STEP_DESCRIPTION);
    step.setVersion(VersionUtil.getVersion());
    esClient.index(
        new IndexRequest.Builder<>()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .document(step)
            .id(PROCESSOR_STEP_ID)
            .refresh(Refresh.True)
            .build());
  }

  private <T> List<T> readRecords(final Class<T> clazz, final String indexName) throws IOException {
    final SearchRequest searchRequest =
        SearchRequest.of(s -> s.size(30).index(indexName).query(Query.of(q -> q.matchAll(m -> m))));
    final SearchResponse<T> searchResponse;
    searchResponse = esClient.search(searchRequest, clazz);

    return searchResponse.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
  }

  private <T> void awaitRecordsArePresent(final Class<T> clazz, final String indexName) {
    Awaitility.await()
        .timeout(Duration.ofSeconds(10))
        .until(() -> !readRecords(clazz, indexName).isEmpty());
  }

  private void assertProcessorStepContentIsStored(final String processDefinitionId)
      throws IOException {
    final var records =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(1);
    assertThat(records.getFirst().getContent()).isEqualTo(String.valueOf(processDefinitionId));
  }
}
