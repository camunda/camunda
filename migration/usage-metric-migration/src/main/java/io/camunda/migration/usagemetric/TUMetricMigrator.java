/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric;

import static io.camunda.migration.usagemetric.client.UsageMetricMigrationClient.TASKLIST_MIGRATOR_STEP_ID;
import static io.camunda.migration.usagemetric.client.UsageMetricMigrationClient.TASKLIST_STEP_DESCRIPTION;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.ids;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import com.google.common.hash.Hashing;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.MigrationTimeoutException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.configuration.MigrationConfiguration;
import io.camunda.migration.commons.configuration.MigrationConfiguration.MigrationRetryConfiguration;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.migration.commons.utils.ExceptionFilter;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.es.ElasticsearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.os.OpensearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.util.MetricRegistry;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import io.camunda.zeebe.util.retry.RetryDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("tasklist-metric-migrator")
public class TUMetricMigrator implements Migrator {
  private static final Logger LOG = LoggerFactory.getLogger(TUMetricMigrator.class);
  private static final String PARAMS_KEY = "hashes";
  private static final String SCRIPT =
      """
      ctx._source.remove("event");
      String value = ctx._source.remove("value");
      ctx._source.assigneeHash = params.hashes[value];
      ctx._source.partitionId = -1;
      """;
  private final MetricRegistry metricRegistry;
  private final UsageMetricMigrationClient client;
  private final IndexDescriptors indexDescriptors;
  private final TasklistMigrationRepositoryIndex tasklistMigrationRepositoryIndex;
  private final MigrationConfiguration configuration;
  private final Instant timeout;
  private final TasklistImportPositionIndex tasklistImportPositionIndex;

  public TUMetricMigrator(
      final ConnectConfiguration connectConfiguration,
      final MeterRegistry meterRegistry,
      final MigrationProperties properties) {
    metricRegistry = new MetricRegistry(meterRegistry);
    final boolean isElasticsearch =
        connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;
    configuration = properties.getMigrationConfiguration(getClass());
    client =
        isElasticsearch
            ? new ElasticsearchUsageMetricMigrationClient(
                connectConfiguration, configuration.getRetry())
            : new OpensearchUsageMetricMigrationClient(
                connectConfiguration, configuration.getRetry());
    indexDescriptors = new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
    tasklistMigrationRepositoryIndex =
        new TasklistMigrationRepositoryIndex(
            connectConfiguration.getIndexPrefix(), isElasticsearch);
    tasklistImportPositionIndex =
        new TasklistImportPositionIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
    timeout = Instant.now().plus(configuration.getTimeout());
  }

  @Override
  public Void call() throws Exception {
    LOG.info("Starting TU metrics migrator");
    try {
      final var reindexTask =
          client.findOne(
              tasklistMigrationRepositoryIndex.getFullQualifiedName(),
              tasklistMigratorStepQuery(),
              ProcessorStep.class);
      var taskId = reindexTask != null ? reindexTask.getContent() : null;
      final var completed = reindexTask != null && reindexTask.isApplied();
      if (reindexTask == null) {
        LOG.info("No TU migration job found, starting new migration");
        busyWaitForImporter();
        taskId = reindex();
      } else {
        if (completed) {
          LOG.info("TU migration job already completed, nothing to do");
          return null;
        } else {
          final var status = client.getTask(taskId);
          LOG.info("TU migration step exists {}, status {}", reindexTask, status);
          if (status.found() && status.completed()) {
            LOG.info("TU migration job {} already completed, nothing to do", taskId);
            client.persistMigratorStep(
                tasklistMigrationRepositoryIndex.getFullQualifiedName(),
                TASKLIST_MIGRATOR_STEP_ID,
                status.taskId(),
                TASKLIST_STEP_DESCRIPTION,
                true);
            return null;
          } else {
            LOG.info(
                "TU migration job {}, is not present in secondary storage, restarting migration",
                taskId);
            taskId = reindex();
          }
        }
      }

      client.persistMigratorStep(
          tasklistMigrationRepositoryIndex.getFullQualifiedName(),
          TASKLIST_MIGRATOR_STEP_ID,
          taskId,
          TASKLIST_STEP_DESCRIPTION,
          false);

      monitorReindexTask(taskId);

    } catch (final Exception e) {
      if (ExceptionFilter.shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
    }
    return null;
  }

  private void busyWaitForImporter() throws Exception {
    final var retryDecorator =
        busyRetryDecorator().withRetryOnException(p -> !(p instanceof MigrationTimeoutException));
    metricRegistry.measureTasklistTaskImporterFinished(
        () ->
            retryDecorator.decorate(
                "Wait for importer to finish", this::isImporterFinished, done -> !done));
  }

  private boolean isImporterFinished() {
    try {
      final var importPositions =
          client.findAll(
              tasklistImportPositionIndex.getFullQualifiedName(),
              tasklistTaskImportPositionQuery(),
              ImportPositionEntity.class);
      final var finished =
          !importPositions.isEmpty()
              && importPositions.stream().allMatch(ImportPositionEntity::getCompleted);
      if (finished) {
        LOG.info("Tasklist importers have concluded.");
        return true;
      } else if (Instant.now().isAfter(timeout)) {
        LOG.error(
            "Importer did not finish within the timeout of {}. Aborting migration.",
            configuration.getTimeout());
        throw new MigrationTimeoutException(
            "Importer did not finish within the timeout of " + configuration.getTimeout());
      }
    } catch (final MigrationException e) {
      LOG.error("Failed to check whether importers are done", e);
    }
    return false;
  }

  private String reindex() {
    final var minEventTime = OffsetDateTime.now().minusYears(2);
    try {
      final var assignees =
          client.getAllAssigneesInMetrics(
              indexDescriptors.get(TasklistMetricIndex.class).getFullQualifiedName());
      final Map<String, Long> hashedAssignees = hashAssignees(assignees);
      return client.reindex(
          indexDescriptors.get(TasklistMetricIndex.class).getFullQualifiedName(),
          indexDescriptors.get(UsageMetricTUIndex.class).getFullQualifiedName(),
          eventDateQuery(minEventTime),
          SCRIPT,
          Map.of(PARAMS_KEY, hashedAssignees));
    } catch (final IOException e) {
      throw new MigrationException("Failed to start reindexing task", e);
    }
  }

  private void monitorReindexTask(final String taskId) throws Exception {
    final var retryDecorator = busyRetryDecorator();
    metricRegistry.measureTasklistReindexTask(
        () ->
            retryDecorator.decorate(
                "Wait for reindex to be completed", () -> pollReindexTask(taskId), done -> !done));
  }

  private boolean pollReindexTask(final String taskId) {
    try {
      final var status = client.getTask(taskId);
      if (status.completed()) {
        LOG.info("Reindex task {} completed successfully", taskId);
        client.persistMigratorStep(
            tasklistMigrationRepositoryIndex.getFullQualifiedName(),
            TASKLIST_MIGRATOR_STEP_ID,
            status.taskId(),
            TASKLIST_STEP_DESCRIPTION,
            true);
        return true;
      }
    } catch (final Exception e) {
      LOG.error("Failed to acquire status of reindexing task", e);
    }
    return false;
  }

  private Map<String, Long> hashAssignees(final Collection<String> assignees) {
    return assignees.stream().collect(Collectors.toMap(assignee -> assignee, this::hashAssignee));
  }

  private long hashAssignee(final String assignee) {
    return Hashing.murmur3_128().hashString(assignee, StandardCharsets.UTF_8).asLong();
  }

  public SearchQuery eventDateQuery(final OffsetDateTime minEventTime) {
    return and(
        SearchQueryBuilders.dateTimeOperations(
            MetricIndex.EVENT_TIME, List.of(Operation.gt(minEventTime))));
  }

  private SearchQuery tasklistMigratorStepQuery() {
    return ids(TASKLIST_MIGRATOR_STEP_ID);
  }

  private SearchQuery tasklistTaskImportPositionQuery() {
    return wildcardQuery(TasklistImportPositionIndex.ID, "*-" + TaskTemplate.INDEX_NAME);
  }

  private RetryDecorator busyRetryDecorator() {
    final var retryConfiguration = new MigrationRetryConfiguration();
    retryConfiguration.setMaxRetries(Integer.MAX_VALUE);
    retryConfiguration.setMinRetryDelay(configuration.getRetry().getMinRetryDelay());
    retryConfiguration.setMaxRetryDelay(configuration.getRetry().getMaxRetryDelay());
    retryConfiguration.setRetryDelayMultiplier(configuration.getRetry().getRetryDelayMultiplier());
    return new RetryDecorator(retryConfiguration);
  }
}
