/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetrics;

import static io.camunda.migration.usagemetric.client.UsageMetricMigrationClient.TASKLIST_MIGRATOR_STEP_ID;
import static io.camunda.search.clients.query.SearchQueryBuilders.ids;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import co.elastic.clients.elasticsearch.core.BulkRequest;
import com.google.common.hash.Hashing;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.MigrationTest;
import io.camunda.migration.commons.configuration.ConfigurationType;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.migration.usagemetric.TUMetricMigrator;
import io.camunda.migration.usagemetric.client.es.ElasticsearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.os.OpensearchUsageMetricMigrationClient;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TUMetricsMigrationIT extends MigrationTest {
  private final ThreadLocalRandom rnd = ThreadLocalRandom.current();
  private final List<String> assignees = new ArrayList<>();
  private final Map<String, Long> documentCountPerAssignee = new HashMap<>();

  @Override
  protected Migrator supplyMigrator(
      final ConnectConfiguration connectConfiguration,
      final MigrationConfiguration migrationConfiguration,
      final MeterRegistry meterRegistry) {
    final var migrationProperties = new MigrationProperties();
    migrationProperties.setMigration(Map.of(ConfigurationType.TU_METRICS, migrationConfiguration));
    return new TUMetricMigrator(connectConfiguration, meterRegistry, migrationProperties);
  }

  @Override
  protected IndexDescriptor[] requiredIndices(final String prefix, final boolean isElasticsearch) {
    return new IndexDescriptor[] {
      new TasklistMetricIndex(prefix, isElasticsearch),
      new TasklistImportPositionIndex(prefix, isElasticsearch),
      new TasklistMigrationRepositoryIndex(prefix, isElasticsearch),
      new UsageMetricTUIndex(prefix, isElasticsearch),
    };
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldPerformMigration(final boolean isElasticsearch) throws Exception {
    // given
    this.isElasticsearch = isElasticsearch;
    generateAssignees(20);
    generateMetricDocuments(2000);

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 2000);

    // when
    runMigration();

    awaitRecordsArePresent(
        UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class), 2000);

    // then
    assertDataMigrated();

    assertProcessorStep(retrieveProcessorStep(), true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldNotRunWhenAlreadyComplete(final boolean isElasticsearch) throws Exception {
    // given - a migration has already run
    this.isElasticsearch = isElasticsearch;
    generateAssignees(20);
    generateMetricDocuments(2000);

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 2000);

    runMigration();

    awaitRecordsArePresent(
        UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class), 2000);

    final var firstRunProcessorStep = retrieveProcessorStep();
    assertProcessorStep(firstRunProcessorStep, true);

    refreshIndices();

    // when - run migration again
    runMigration();

    // then - data is migrated
    assertDataMigrated();

    final var secondRunProcessorStep = retrieveProcessorStep();
    assertProcessorStep(secondRunProcessorStep, true);

    // and - referenced reindex task id is not changed
    assertThat(secondRunProcessorStep.getContent()).isEqualTo(firstRunProcessorStep.getContent());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRerunIfStepIsMissing(final boolean isElasticsearch) throws Exception {
    // given - migration has already run
    this.isElasticsearch = isElasticsearch;
    generateAssignees(20);
    generateMetricDocuments(2000);

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 2000);

    runMigration();

    awaitRecordsArePresent(
        UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class), 2000);

    final var firstRunTracker = retrieveProcessorStep();
    assertProcessorStep(firstRunTracker, true);

    // but relevant processor step is not present
    deleteProcessorStep();
    refreshIndices();

    // when - run migration again
    runMigration();

    // then - data is migrated
    assertDataMigrated();

    final var secondRunTracker = retrieveProcessorStep();
    assertProcessorStep(secondRunTracker, true);

    // and - referenced reindex task id is different
    assertThat(secondRunTracker.getContent()).isNotEqualTo(firstRunTracker.getContent());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldNotRerunIfTaskIsCompletedInSecondaryStorage(final boolean isElasticsearch)
      throws Exception {
    // given - migration has already run
    this.isElasticsearch = isElasticsearch;
    generateAssignees(20);
    generateMetricDocuments(2000);

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 2000);

    runMigration();

    awaitRecordsArePresent(
        UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class), 2000);

    final var firstRunProcessorStep = retrieveProcessorStep();
    assertProcessorStep(firstRunProcessorStep, true);

    // but relevant processor step is not marked as completed
    updateProcessorStep(firstRunProcessorStep.getContent(), false);
    refreshIndices();

    final var updatedStep = retrieveProcessorStep();
    assertProcessorStep(updatedStep, false);
    assertThat(updatedStep.getContent()).isEqualTo(firstRunProcessorStep.getContent());

    // when - run migration again
    runMigration();

    // then - data is migrated
    assertDataMigrated();

    final var secondRunProcessorStep = retrieveProcessorStep();
    assertProcessorStep(secondRunProcessorStep, true);

    // and - relevant reindex task id is unchanged as it's marked as completed on secondary storage
    assertThat(secondRunProcessorStep.getContent()).isEqualTo(firstRunProcessorStep.getContent());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldRerunIfTaskIsNotCompletedInSecondaryStorage(final boolean isElasticsearch)
      throws Exception {
    // given - migration has already run but the task id is not present on secondary storage
    this.isElasticsearch = isElasticsearch;
    generateAssignees(20);
    generateMetricDocuments(2000);

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 2000);

    final String nodeId = getStorageNodeId();
    updateProcessorStep(nodeId + ":123", false);
    refreshIndices();

    final var updatedTracker = retrieveProcessorStep();
    assertProcessorStep(updatedTracker, false);
    assertThat(updatedTracker.getContent()).isEqualTo(nodeId + ":123");

    // when - migration is run
    runMigration();

    assertDataMigrated();

    final var secondRunProcessorStep = retrieveProcessorStep();
    assertProcessorStep(secondRunProcessorStep, true);

    // then - relevant reindex task id is changed indicating that the reindex has run again
    assertThat(secondRunProcessorStep.getContent()).isNotEqualTo(nodeId + ":123");
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldFailOnImporterFinishedTimeout(final boolean isElasticsearch) {
    // given - importer is not finished
    this.isElasticsearch = isElasticsearch;
    properties.getRetry().setMinRetryDelay(Duration.ofSeconds(1));
    properties.setTimeout(Duration.ofSeconds(5));

    // when - then the migration should fail with a timeout exception
    final var maxTimeout = properties.getTimeout().getSeconds() * 2;
    Awaitility.await()
        .atLeast(properties.getTimeout().getSeconds() - 1, TimeUnit.SECONDS)
        .atMost(maxTimeout, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThatExceptionOfType(MigrationException.class)
                    .isThrownBy(this::runMigration)
                    .withCauseInstanceOf(CompletionException.class)
                    .withMessageContaining("Importer did not finish within the timeout of"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldNotMigrateOlderThanTwoYearMetrics(final boolean isElasticsearch) throws Exception {
    this.isElasticsearch = isElasticsearch;
    // given - an older metric document is present
    final MetricEntity metric =
        new MetricEntity()
            .setEvent("task_completed_by_assignee")
            .setValue("test-value")
            .setEventTime(OffsetDateTime.now().minusYears(2).minusDays(1))
            .setTenantId("<default>");

    writeImportPositionToIndex(
        indexFqnForClass(TasklistImportPositionIndex.class), importPosition(true, 1));

    if (isElasticsearch) {
      esClient.index(
          idx -> idx.index(indexFqnForClass(TasklistMetricIndex.class)).document(metric));
    } else {
      osClient.index(
          idx -> idx.index(indexFqnForClass(TasklistMetricIndex.class)).document(metric));
    }

    refreshIndices();

    awaitRecordsArePresent(MetricEntity.class, indexFqnForClass(TasklistMetricIndex.class), 1);

    // when - migration is run
    runMigration();

    // then - no data is migrated
    refreshIndices();
    assertThat(readRecords(UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class)))
        .isEmpty();
  }

  private void generateAssignees(final int assigneeCount) {
    assignees.clear();
    for (int i = 0; i < assigneeCount; i++) {
      final int len = rnd.nextInt(1, 7); // 1..6
      final StringBuilder sb = new StringBuilder(len);
      for (int j = 0; j < len; j++) {
        sb.append((char) ('a' + rnd.nextInt(26)));
      }
      assignees.add(sb.toString());
    }
  }

  private void generateMetricDocuments(final int documentCount) throws IOException {
    documentCountPerAssignee.clear();
    final List<MetricEntity> docs = new ArrayList<>(documentCount);
    for (int i = 0; i < documentCount; i++) {
      final String assignee = assignees.get(rnd.nextInt(assignees.size()));

      final MetricEntity metric =
          new MetricEntity()
              .setEvent("task_completed_by_assignee")
              .setValue(assignee)
              .setTenantId("<default>");

      docs.add(metric);
      documentCountPerAssignee.merge(assignee, 1L, Long::sum);
    }
    if (isElasticsearch) {
      final var req = new BulkRequest.Builder();
      docs.forEach(
          doc ->
              req.operations(
                  o ->
                      o.index(
                          i ->
                              i.index(indexFqnForClass(TasklistMetricIndex.class)).document(doc))));

      esClient.bulk(req.build());
    } else {
      final var req = new org.opensearch.client.opensearch.core.BulkRequest.Builder();
      docs.forEach(
          doc ->
              req.operations(
                  o ->
                      o.index(
                          i ->
                              i.index(indexFqnForClass(TasklistMetricIndex.class)).document(doc))));

      osClient.bulk(req.build());
    }
  }

  private ProcessorStep retrieveProcessorStep() {
    final var migrationClient =
        isElasticsearch
            ? new ElasticsearchUsageMetricMigrationClient(ES_CONFIGURATION, properties.getRetry())
            : new OpensearchUsageMetricMigrationClient(OS_CONFIGURATION, properties.getRetry());

    return migrationClient.findOne(
        indexFqnForClass(TasklistMigrationRepositoryIndex.class),
        tasklistMigratorStepQuery(),
        ProcessorStep.class);
  }

  private void updateProcessorStep(final String taskId, final boolean completed) {
    final ProcessorStep step = new ProcessorStep();
    step.setApplied(completed);
    step.setContent(taskId);
    try {
      if (isElasticsearch) {
        esClient.update(
            r ->
                r.index(indexFqnForClass(TasklistMigrationRepositoryIndex.class))
                    .id(TASKLIST_MIGRATOR_STEP_ID)
                    .docAsUpsert(true)
                    .doc(step),
            ProcessorStep.class);
      } else {
        osClient.update(
            r ->
                r.index(indexFqnForClass(TasklistMigrationRepositoryIndex.class))
                    .docAsUpsert(true)
                    .doc(step)
                    .id(TASKLIST_MIGRATOR_STEP_ID),
            ProcessorStep.class);
      }
    } catch (final Exception e) {
      // Ignore, step might not exist
    }
  }

  private void assertProcessorStep(final ProcessorStep tracker, final boolean completed) {
    assertThat(tracker).isNotNull();
    assertThat(tracker.isApplied()).isEqualTo(completed);
    assertThat(tracker.getContent()).isNotBlank();
  }

  private void deleteProcessorStep() {
    try {
      if (isElasticsearch) {
        esClient.delete(
            d ->
                d.index(indexFqnForClass(TasklistMigrationRepositoryIndex.class))
                    .id(TASKLIST_MIGRATOR_STEP_ID));
      } else {
        osClient.delete(
            d ->
                d.index(indexFqnForClass(TasklistMigrationRepositoryIndex.class))
                    .id(TASKLIST_MIGRATOR_STEP_ID));
      }
    } catch (final Exception e) {
      // Ignore, step might not exist
    }
  }

  private void assertDataMigrated() throws IOException {
    final var migratedMetrics =
        readRecords(UsageMetricsTUEntity.class, indexFqnForClass(UsageMetricTUIndex.class));

    final var migratedMetricsCountByAssignee =
        migratedMetrics.stream()
            .collect(
                Collectors.groupingBy(
                    UsageMetricsTUEntity::getAssigneeHash, Collectors.counting()));

    documentCountPerAssignee.forEach(
        (assignee, documentCount) -> {
          final var stringHash =
              Hashing.murmur3_128().hashString(assignee, StandardCharsets.UTF_8).asLong();

          final var migratedCount = migratedMetricsCountByAssignee.get(stringHash);
          if (migratedCount != null) {
            assertThat(migratedCount).isEqualTo(documentCountPerAssignee.get(assignee));
          }
        });
  }

  private String getStorageNodeId() throws IOException {
    if (isElasticsearch) {
      return esClient.nodes().info().nodes().keySet().stream().findFirst().orElse("");
    } else {
      return osClient.nodes().info().nodes().keySet().stream().findFirst().orElse("");
    }
  }

  private static ImportPositionEntity importPosition(final boolean completed, final int partition) {
    return new ImportPositionEntity()
        .setId(partition + "-" + TaskTemplate.INDEX_NAME)
        .setPartitionId(partition)
        .setAliasName(TaskTemplate.INDEX_NAME)
        .setIndexName(TaskTemplate.INDEX_NAME)
        .setCompleted(completed);
  }

  private SearchQuery tasklistMigratorStepQuery() {
    return ids(TASKLIST_MIGRATOR_STEP_ID);
  }
}
