/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.process.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.process.ProcessMigrator;
import io.camunda.migration.process.TestData;
import io.camunda.migration.process.adapter.Adapter;
import io.camunda.migration.process.adapter.ProcessorStep;
import io.camunda.migration.process.adapter.es.ElasticsearchAdapter;
import io.camunda.migration.process.adapter.os.OpensearchAdapter;
import io.camunda.migration.process.util.MigrationUtil;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(Lifecycle.PER_CLASS)
public class ProcessMigratorIT extends AdapterTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void singleMigrationRound(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setBatchSize(1);
    final Adapter adapter =
        isElasticsearch
            ? new ElasticsearchAdapter(properties, ES_CONFIGURATION)
            : new OpensearchAdapter(properties, OS_CONFIGURATION);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithPublicFormId(1L);
    final ProcessEntity entityNotToBeMigrated = TestData.processEntityWithPublicFormId(2L);
    writeProcessToIndex(entityToBeMigrated);
    writeProcessToIndex(entityNotToBeMigrated);
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 2);

    // when
    final String migratedEntityId =
        adapter.migrate(List.of(MigrationUtil.migrate(entityToBeMigrated)));
    adapter.writeLastMigratedEntity(migratedEntityId);
    awaitRecordsArePresent(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName(), 1);
    refreshIndices();

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
    adapter.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateSuccessfully(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    writeProcessToIndex(TestData.processEntityWithPublicFormId(1L));
    writeProcessToIndex(TestData.processEntityWithoutForm(2L));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(3L));
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 3);
    // when
    runMigration();
    refreshIndices();

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateSuccessfullyWithMultipleRounds(final boolean isElasticsearch)
      throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    for (int i = 1; i <= 20; i++) {
      writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
    }
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 20);

    // when
    runMigration();
    refreshIndices();

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateFromStoredId(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    for (int i = 1; i <= 9; i++) {
      writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
    }
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 9);
    writeProcessorStepToIndex("5");
    // when
    runMigration();
    refreshIndices();

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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldNotMigrateWhenFinalStepIsPresent(final boolean isElasticsearch)
      throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setBatchSize(1);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithPublicFormId(1L);
    final ProcessEntity entityNotToBeMigrated = TestData.processEntityWithPublicFormId(2L);
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    writeProcessorStepToIndex("2");
    writeProcessToIndex(entityToBeMigrated);
    writeProcessToIndex(entityNotToBeMigrated);
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 2);

    // when
    runMigration();
    refreshIndices();

    // then
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    final var stepRecords =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(2);
    assertThat(records.stream().allMatch(r -> r.getIsPublic() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormId() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormKey() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getIsFormEmbedded() == null)).isTrue();
    assertThat(stepRecords.size()).isEqualTo(1);
    assertThat(stepRecords.getFirst().getContent()).isEqualTo("2");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldMigrateDuringCountdown(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setImporterFinishedTimeout(Duration.ofSeconds(2));
    properties.getRetry().setMinRetryDelay(Duration.ofSeconds(1));
    properties.getRetry().setMaxRetryDelay(Duration.ofSeconds(1));
    properties.setBatchSize(4);
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
        1,
        TimeUnit.SECONDS);

    // when
    runMigration();
    refreshIndices();
    scheduler.shutdown();

    // then
    assertProcessorStepContentIsStored("9");
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());
    assertThat(records.size()).isEqualTo(9);
    assertThat(records.stream().allMatch(r -> r.getIsPublic().equals(Boolean.TRUE))).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormId().equals("testForm"))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteWhenImportPositionIsUpdated(final boolean isElasticsearch)
      throws IOException, InterruptedException {
    this.isElasticsearch = isElasticsearch;
    properties.setImporterFinishedTimeout(Duration.ofSeconds(5));
    writeImportPositionToIndex(
        TestData.completedImportPosition(1), TestData.notCompletedImportPosition(2));
    esClient.indices().refresh();
    awaitRecordsArePresent(
        ImportPositionEntity.class, importPositionIndex.getFullQualifiedName(), 2);
    final var latch = new CountDownLatch(1);

    new Thread(
            () -> {
              runMigration();
              latch.countDown();
            })
        .start();

    assertThat(latch.getCount()).isEqualTo(1);
    writeImportPositionToIndex(TestData.completedImportPosition(2));
    latch.await();
    assertThat(latch.getCount()).isEqualTo(0);

    final var records =
        readRecords(ProcessorStep.class, migrationRepositoryIndex.getFullQualifiedName());
    assertThat(records).isEmpty();
    final var importPositionRecords =
        readRecords(ImportPositionEntity.class, importPositionIndex.getFullQualifiedName());
    assertThat(importPositionRecords.size()).isEqualTo(2);
    assertThat(importPositionRecords.stream().allMatch(ImportPositionEntity::getCompleted))
        .isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldKeepRunningUntilImportPositionTimeout(final boolean isElasticsearch)
      throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setImporterFinishedTimeout(Duration.ofSeconds(5));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(1L));
    writeProcessToIndex(TestData.processEntityWithPublicFormKey(2L));
    writeImportPositionToIndex(TestData.completedImportPosition(1));
    refreshIndices();
    awaitRecordsArePresent(
        ImportPositionEntity.class, importPositionIndex.getFullQualifiedName(), 1);

    // when
    Awaitility.await()
        .atMost(Duration.ofSeconds(properties.getImporterFinishedTimeout().getSeconds() * 2))
        .atLeast(properties.getImporterFinishedTimeout())
        .until(
            () -> {
              runMigration();
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

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowException(final boolean isElasticsearch) {
    this.isElasticsearch = isElasticsearch;
    final ConnectConfiguration connectConfiguration = new ConnectConfiguration();

    if (!isElasticsearch) {
      connectConfiguration.setType("opensearch");
    }
    // invalid URL
    connectConfiguration.setUrl("http://localhost:3333");
    final var migrator = new ProcessMigrator(properties, connectConfiguration, meterRegistry);
    properties.getRetry().setMaxRetries(2);
    properties.getRetry().setMinRetryDelay(Duration.ofSeconds(1));

    final var ex =
        assertThatExceptionOfType(MigrationException.class).isThrownBy(migrator::call).actual();
    assertThat(ex.getMessage()).isEqualTo("Failed to fetch last migrated process");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldNotFlushStepOnError(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;

    if (isElasticsearch) {
      ES_CONFIGURATION.setIndexPrefix(MISCONFIGURED_PREFIX);
    } else {
      OS_CONFIGURATION.setIndexPrefix(MISCONFIGURED_PREFIX);
    }

    final Adapter adapter =
        isElasticsearch
            ? new ElasticsearchAdapter(properties, ES_CONFIGURATION)
            : new OpensearchAdapter(properties, OS_CONFIGURATION);
    final ProcessEntity entityToBeMigrated = TestData.processEntityWithPublicFormId(1L);
    writeToMisconfiguredProcessToIndex(entityToBeMigrated);

    // when
    final String migratedEntityId =
        adapter.migrate(List.of(MigrationUtil.migrate(entityToBeMigrated)));

    // then
    assertThat(migratedEntityId).isNull();

    if (isElasticsearch) {
      ES_CONFIGURATION.setIndexPrefix(null);
    } else {
      OS_CONFIGURATION.setIndexPrefix(null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldTimeoutMigration(final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setTimeout(Duration.ofSeconds(5));

    final var migrator =
        new ProcessMigrator(
            properties, isElasticsearch ? ES_CONFIGURATION : OS_CONFIGURATION, meterRegistry);
    writeImportPositionToIndex(TestData.notCompletedImportPosition(1));

    // when - then
    final var maxTimeout =
        properties.getTimeout().getSeconds()
            + properties.getRetry().getMinRetryDelay().getSeconds()
            + 1;
    Awaitility.await()
        .atLeast(properties.getTimeout().getSeconds() - 1, TimeUnit.SECONDS)
        .atMost(maxTimeout, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThatExceptionOfType(MigrationException.class)
                    .isThrownBy(migrator::call)
                    .withCauseInstanceOf(MigrationTimeoutException.class)
                    .withMessageContaining("Process Migration timed out"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldTimeoutAfterMigratingDataWithoutImportersCompleted(
      final boolean isElasticsearch) throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setTimeout(Duration.ofSeconds(1));

    final var migrator =
        new ProcessMigrator(
            properties, isElasticsearch ? ES_CONFIGURATION : OS_CONFIGURATION, meterRegistry);
    writeImportPositionToIndex(TestData.notCompletedImportPosition(1));

    for (int i = 1; i < 10; i++) {
      writeProcessToIndex(TestData.processEntityWithPublicFormId((long) i));
    }
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 9);

    // when -  then
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThatExceptionOfType(MigrationException.class)
                    .isThrownBy(migrator::call)
                    .withCauseInstanceOf(MigrationTimeoutException.class)
                    .withMessageContaining("Process Migration timed out"));
    refreshIndices();

    // and
    final var records = readRecords(ProcessEntity.class, processIndex.getFullQualifiedName());

    assertProcessorStepContentIsStored("9");
    assertThat(records).hasSize(9);
    assertThat(records.stream().noneMatch(r -> r.getIsPublic().equals(Boolean.FALSE))).isTrue();
    assertThat(records.stream().noneMatch(r -> r.getFormId() == null)).isTrue();
    assertThat(records.stream().allMatch(r -> r.getFormKey() == null)).isTrue();
    assertThat(records.stream().noneMatch(ProcessEntity::getIsFormEmbedded)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldNotTimeoutIfCountdownIsEngaged(final boolean isElasticsearch)
      throws IOException {
    // given
    this.isElasticsearch = isElasticsearch;
    properties.setImporterFinishedTimeout(Duration.ofSeconds(10));
    properties.setTimeout(Duration.ofSeconds(1));

    final var migrator =
        new ProcessMigrator(
            properties, isElasticsearch ? ES_CONFIGURATION : OS_CONFIGURATION, meterRegistry);
    writeImportPositionToIndex(TestData.completedImportPosition(1));

    writeProcessToIndex(TestData.processEntityWithPublicFormId(1L));
    awaitRecordsArePresent(ProcessEntity.class, processIndex.getFullQualifiedName(), 1);

    // when -  then
    assertThatNoException().isThrownBy(migrator::call);
  }
}
