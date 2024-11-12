/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process;

import static io.camunda.migration.process.adapter.Adapter.PROCESSOR_STEP_ID;
import static io.camunda.migration.process.adapter.Adapter.STEP_DESCRIPTION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.operate.schema.migration.ProcessorStep;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OpensearchMigrationRunnerIT {

  @Container
  private static final OpensearchContainer<?> OS_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static OpenSearchClient osClient;
  private static MigrationRunner migrator;
  private static ProcessMigrationProperties properties;
  private static ProcessIndex processIndex;
  private static MigrationRepositoryIndex migrationRepositoryIndex;

  @BeforeAll
  public static void setUp() throws IOException {
    properties = new ProcessMigrationProperties();
    properties.setBatchSize(5);
    final ConnectConfiguration connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setType("opensearch");
    connectConfiguration.setUrl("http://localhost:" + OS_CONTAINER.getMappedPort(9200));
    properties.setConnect(connectConfiguration);
    final var connector = new OpensearchConnector(properties.getConnect());
    osClient = connector.createClient();
    createIndices();
  }

  private static void createIndices() {

    final OpensearchEngineClient es = new OpensearchEngineClient(osClient);
    processIndex = new ProcessIndex(properties.getConnect().getIndexPrefix(), false);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(properties.getConnect().getIndexPrefix(), false);

    es.createIndex(processIndex, new IndexSettings());
    es.createIndex(migrationRepositoryIndex, new IndexSettings());
  }

  @BeforeEach
  public void cleanUp() throws IOException {
    properties.setBatchSize(5);
    migrator = new MigrationRunner(properties);
    osClient.deleteByQuery(
        DeleteByQueryRequest.of(
            d ->
                d.index(processIndex.getFullQualifiedName())
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.matchAll(m -> m))));
    osClient.deleteByQuery(
        DeleteByQueryRequest.of(
            d ->
                d.index(migrationRepositoryIndex.getFullQualifiedName())
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.matchAll(m -> m))));
    osClient.indices().refresh();
  }

  @Test
  public void shouldMigrateSuccessfully() throws IOException {
    // when
    writeProcessToIndex(TestData.processEntityWithForm(1L));
    writeProcessToIndex(TestData.processEntityWithoutForm(2L));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    osClient.indices().refresh();
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
    osClient.indices().refresh();
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
  public void migrationShouldCompleteWithMultipleRounds() throws IOException {
    // when
    for (int i = 1; i <= 20; i++) {
      writeProcessToIndex(TestData.processEntityWithForm((long) i));
    }
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    osClient.indices().refresh();

    // verify
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    // Since the key field is marked as a keyword in ES/OS the sorting is done lexicographically
    assertProcessorStepContentIsStored("9");
    assertThat(records.size()).isEqualTo(20);
    assertThat(records.stream().noneMatch(r -> r.getIsPublic().equals(Boolean.FALSE))).isTrue();
    assertThat(records.stream().noneMatch(r -> r.getFormId() == null)).isTrue();
  }

  @Test
  public void migrationShouldDoNothingWhenFinalStepIsPresent() throws IOException {
    // when
    properties.setBatchSize(1);
    writeProcessorStepToIndex("2");
    writeProcessToIndex(TestData.processEntityWithForm(1L));
    writeProcessToIndex(TestData.processEntityWithForm(2L));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // then
    migrator.run();
    osClient.indices().refresh();

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
    osClient.indices().refresh();

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

  private void writeProcessToIndex(final ProcessEntity entity) throws IOException {
    osClient.index(
        new IndexRequest.Builder()
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
    osClient.index(
        new IndexRequest.Builder<>()
            .index(migrationRepositoryIndex.getFullQualifiedName())
            .document(step)
            .id(PROCESSOR_STEP_ID)
            .refresh(Refresh.True)
            .build());
  }

  private <T> List<T> readRecords(final Class<T> clazz, final String indexName) throws IOException {
    final SearchRequest.Builder searchRequest =
        new SearchRequest.Builder().index(indexName).size(30).query(q -> q.matchAll(m -> m));
    final SearchResponse<T> searchResponse;

    searchResponse = osClient.search(searchRequest.build(), clazz);
    return searchResponse.hits().hits().stream().map(Hit::source).toList();
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
