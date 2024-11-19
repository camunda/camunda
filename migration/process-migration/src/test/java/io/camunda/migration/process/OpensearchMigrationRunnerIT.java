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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.exporter.config.ExporterConfiguration.IndexSettings;
import io.camunda.exporter.schema.opensearch.OpensearchEngineClient;
import io.camunda.migration.api.MigrationException;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.operate.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.entities.operate.ImportPositionEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import io.camunda.zeebe.util.VersionUtil;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
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
@TestMethodOrder(OrderAnnotation.class)
public class OpensearchMigrationRunnerIT {

  @Container
  private static final OpensearchContainer<?> OS_CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  private static OpenSearchClient osClient;
  private static MigrationRunner migrator;
  private static ProcessMigrationProperties properties;
  private static ConnectConfiguration connectConfiguration;
  private static ProcessIndex processIndex;
  private static MigrationRepositoryIndex migrationRepositoryIndex;
  private static ImportPositionIndex importPositionIndex;

  @BeforeAll
  public static void setUp() throws IOException {
    properties = new ProcessMigrationProperties();
    properties.setBatchSize(5);
    properties.setMaxRetryDelay(Duration.ofSeconds(2));
    properties.setPostImporterTimeout(Duration.ofSeconds(1));
    connectConfiguration = new ConnectConfiguration();
    connectConfiguration.setType("opensearch");
    connectConfiguration.setUrl("http://localhost:" + OS_CONTAINER.getMappedPort(9200));
    osClient = new OpensearchConnector(connectConfiguration).createClient();
    createIndices();
  }

  private static void createIndices() {
    final OpensearchEngineClient es = new OpensearchEngineClient(osClient);
    processIndex = new ProcessIndex(connectConfiguration.getIndexPrefix(), false);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), false);
    importPositionIndex = new ImportPositionIndex(connectConfiguration.getIndexPrefix(), false);

    es.createIndex(processIndex, new IndexSettings());
    es.createIndex(migrationRepositoryIndex, new IndexSettings());
    es.createIndex(importPositionIndex, new IndexSettings());
  }

  @BeforeEach
  public void cleanUp() throws IOException {
    properties.setBatchSize(5);
    migrator = new MigrationRunner(properties, connectConfiguration);
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
    osClient.deleteByQuery(
        DeleteByQueryRequest.of(
            d ->
                d.index(importPositionIndex.getFullQualifiedName())
                    .conflicts(Conflicts.Proceed)
                    .query(q -> q.matchAll(m -> m))));
    osClient.indices().refresh();
  }

  @Test
  public void singleMigrationRound() throws IOException {
    // given
    properties.setBatchSize(1);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithPublicFormId(1L);
    final ProcessEntity entityNotToBeMigrated = TestData.processEntityWithPublicFormId(2L);
    writeProcessToIndex(entityToBeMigrated);
    writeProcessToIndex(entityNotToBeMigrated);
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());

    // when
    migrator.migrateBatch(List.of(entityToBeMigrated));
    awaitRecordsArePresent(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    osClient.indices().refresh();

    // then
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
    assertThat(processRecords.stream().filter(r -> r.getKey() == 1).findFirst().get().getFormKey())
        .isNull();
    assertThat(
            processRecords.stream()
                .filter(r -> r.getKey() == 1)
                .findFirst()
                .get()
                .getIsFormEmbedded())
        .isFalse();

    assertThat(processRecords.stream().filter(r -> r.getKey() == 2).findFirst().get().getIsPublic())
        .isNull();
    assertThat(processRecords.stream().filter(r -> r.getKey() == 2).findFirst().get().getFormId())
        .isNull();
    assertThat(processRecords.stream().filter(r -> r.getKey() == 2).findFirst().get().getFormKey())
        .isNull();
    assertThat(
            processRecords.stream()
                .filter(r -> r.getKey() == 2)
                .findFirst()
                .get()
                .getIsFormEmbedded())
        .isNull();
  }

  @Test
  public void shouldMigrateSuccessfully() throws IOException {
    // given
    writeProcessToIndex(TestData.processEntityWithPublicFormId(1L));
    writeProcessToIndex(TestData.processEntityWithoutForm(2L));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(3L));
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // when
    migrator.run();
    osClient.indices().refresh();
    // then
    assertProcessorStepContentIsStored("3");

    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(3);
    /* Assertions for Process with FormId reference */
    assertThat(records.stream().filter(r -> r.getKey() == 1).findFirst().get().getIsPublic())
        .isTrue();
    assertThat(records.stream().filter(r -> r.getKey() == 1).findFirst().get().getFormId())
        .isEqualTo("testForm");
    assertThat(records.stream().filter(r -> r.getKey() == 1).findFirst().get().getFormKey())
        .isNull();
    assertThat(records.stream().filter(r -> r.getKey() == 1).findFirst().get().getIsFormEmbedded())
        .isFalse();
    /* Assertions for Process with no Form references */
    assertThat(records.stream().filter(r -> r.getKey() == 2).findFirst().get().getIsPublic())
        .isFalse();
    assertThat(records.stream().filter(r -> r.getKey() == 2).findFirst().get().getFormId())
        .isNull();
    assertThat(records.stream().filter(r -> r.getKey() == 2).findFirst().get().getFormKey())
        .isNull();
    assertThat(records.stream().filter(r -> r.getKey() == 2).findFirst().get().getIsFormEmbedded())
        .isFalse();
    /* Assertions for Process with FormKey reference */
    assertThat(records.stream().filter(r -> r.getKey() == 3).findFirst().get().getIsPublic())
        .isTrue();
    assertThat(records.stream().filter(r -> r.getKey() == 3).findFirst().get().getFormKey())
        .isEqualTo("camunda-forms:bpmn:testForm");
    assertThat(records.stream().filter(r -> r.getKey() == 3).findFirst().get().getFormId())
        .isNull();
    assertThat(records.stream().filter(r -> r.getKey() == 3).findFirst().get().getIsFormEmbedded())
        .isTrue();
  }

  @Test
  public void shouldMigrateSuccessfullyWithMultipleRounds() throws IOException {
    // given
    for (int i = 1; i <= 20; i++) {
      writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
    }
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // when
    migrator.run();
    osClient.indices().refresh();

    // then
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    // Since the key field is marked as a keyword in ES/OS the sorting is done lexicographically
    assertProcessorStepContentIsStored("9");
    assertThat(records.size()).isEqualTo(20);
    assertThat(records.stream().noneMatch(r -> r.getIsPublic().equals(Boolean.FALSE))).isTrue();
    assertThat(records.stream().noneMatch(r -> r.getFormId() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormKey() == null)).isTrue();
    assertThat(records.stream().noneMatch(ProcessEntity::getIsFormEmbedded)).isTrue();
  }

  @Test
  public void shouldMigrateFromStoredId() throws IOException {
    // given
    for (int i = 1; i <= 9; i++) {
      writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
    }
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    writeProcessorStepToIndex("5");
    // when
    migrator.run();
    osClient.indices().refresh();

    // then
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    // Since the key field is marked as a keyword in ES/OS the sorting is done lexicographically
    assertProcessorStepContentIsStored("9");
    assertThat(records.size()).isEqualTo(9);
    assertThat(records.stream().filter(r -> r.getKey() <= 5).allMatch(r -> r.getIsPublic() == null))
        .isTrue();
    assertThat(records.stream().filter(r -> r.getKey() <= 5).allMatch(r -> r.getFormId() == null))
        .isTrue();
    assertThat(records.stream().filter(r -> r.getKey() <= 5).allMatch(r -> r.getFormKey() == null))
        .isTrue();
    assertThat(
            records.stream()
                .filter(r -> r.getKey() <= 5)
                .allMatch(r -> r.getIsFormEmbedded() == null))
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
    assertThat(records.stream().filter(r -> r.getKey() > 5).allMatch(r -> r.getFormKey() == null))
        .isTrue();
    assertThat(
            records.stream()
                .filter(r -> r.getKey() > 5)
                .allMatch(r -> r.getIsFormEmbedded().equals(Boolean.FALSE)))
        .isTrue();
  }

  @Test
  public void shouldNotMigrateWhenFinalStepIsPresent() throws IOException {
    // given
    properties.setBatchSize(1);
    writeProcessorStepToIndex("2");
    writeProcessToIndex(TestData.processEntityWithPublicFormId(1L));
    writeProcessToIndex(TestData.processEntityWithPublicFormId(2L));
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName());
    // when
    migrator.run();
    osClient.indices().refresh();

    // then
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
  public void shouldMigrateDuringCountdown() throws IOException {
    // given
    properties.setPostImporterTimeout(Duration.ofSeconds(2));
    properties.setMinRetryDelay(Duration.ofSeconds(3));
    properties.setBatchSize(2);
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    scheduler.schedule(
        () -> {
          try {
            for (int i = 1; i <= 9; i++) {
              writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
            }
          } catch (final IOException e) {
            throw new RuntimeException(e);
          }
        },
        2,
        TimeUnit.SECONDS);

    // when
    migrator.run();
    scheduler.shutdown();
    osClient.indices().refresh();

    // then
    assertProcessorStepContentIsStored("9");
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(9);
    assertThat(records.stream().allMatch(r -> r.getIsPublic().equals(Boolean.TRUE))).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormId().equals("testForm"))).isTrue();
  }

  @Test
  public void shouldRunIndefinitelyWhenANonCompletedImportPositionExists() throws IOException {
    writeImportPositionToIndex(
        TestData.notCompletedImportPosition(1), TestData.notCompletedImportPosition(2));
    osClient.indices().refresh();
    awaitRecordsArePresent(ImportPositionEntity.class, importPositionIndex.getFullQualifiedName());

    assertThrows(
        ConditionTimeoutException.class,
        () ->
            Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .until(
                    () -> {
                      migrator.run();
                      return true;
                    }));
  }

  @Test
  public void shouldKeepRunningUntilImportPositionTimeout() throws IOException {
    // given
    properties.setPostImporterTimeout(Duration.ofSeconds(10));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(1L));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(2L));
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    osClient.indices().refresh();
    awaitRecordsArePresent(ImportPositionEntity.class, importPositionIndex.getFullQualifiedName());

    // when
    Awaitility.await()
        .atMost(Duration.ofSeconds(properties.getPostImporterTimeout().getSeconds() * 2))
        .atLeast(properties.getPostImporterTimeout())
        .until(
            () -> {
              migrator.run();
              return true;
            });

    // then
    assertProcessorStepContentIsStored("2");
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(2);
    assertThat(records.stream().allMatch(r -> r.getIsPublic().equals(Boolean.TRUE))).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormKey().equals("camunda-forms:bpmn:testForm")))
        .isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormId() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getIsFormEmbedded().equals(Boolean.TRUE))).isTrue();
  }

  @Test
  @Order(Integer.MAX_VALUE)
  public void shouldThrowException() {
    OS_CONTAINER.close();
    properties.setMaxRetries(2);

    final var ex = assertThrows(MigrationException.class, migrator::run);
    assertThat(ex.getMessage()).isEqualTo("Failed to fetch last migrated process");
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

  private void writeImportPositionToIndex(final ImportPositionEntity... importPositionEntities)
      throws IOException {
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

    osClient.bulk(req.build());
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
