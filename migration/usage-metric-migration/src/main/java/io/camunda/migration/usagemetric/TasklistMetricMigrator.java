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
import static io.camunda.migration.usagemetric.util.MetricRegistry.TASKLIST_REINDEX_TASK;
import static io.camunda.migration.usagemetric.util.MetricRegistry.TASKLIST_TASK_IMPORTER_FINISHED;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import com.google.common.hash.Hashing;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.TasklistMigrationRepositoryIndex;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.TasklistMetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricTUIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component("tasklist-metric-migrator")
public class TasklistMetricMigrator extends MetricMigrator {

  private static final String PARAMS_KEY = "hashes";
  private static final String SCRIPT =
      """
      ctx._source.remove("event");
      String value = ctx._source.remove("value");
      ctx._source.assigneeHash = params.hashes[value];
      ctx._source.partitionId = -1;

      ctx._source.startTime = ctx._source.eventTime;
      ctx._source.endTime = ctx._source.eventTime;
      ctx._source.remove("eventTime");
      """;
  private final TasklistMigrationRepositoryIndex tasklistMigrationRepositoryIndex;
  private final TasklistImportPositionIndex tasklistImportPositionIndex;

  public TasklistMetricMigrator(
      final ConnectConfiguration connectConfiguration,
      final MeterRegistry meterRegistry,
      final MigrationProperties properties) {
    super(connectConfiguration, meterRegistry, properties);
    tasklistMigrationRepositoryIndex =
        new TasklistMigrationRepositoryIndex(
            connectConfiguration.getIndexPrefix(), isElasticsearch);
    tasklistImportPositionIndex =
        new TasklistImportPositionIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
  }

  @Override
  protected String getShortName() {
    return "TU";
  }

  @Override
  protected IndexDescriptor getImportPositionIndex() {
    return tasklistImportPositionIndex;
  }

  @Override
  protected SearchQuery getImportPositionQuery() {
    return wildcardQuery(TasklistImportPositionIndex.ID, "*-" + TaskTemplate.INDEX_NAME);
  }

  @Override
  protected String getMeterImporterFinishedTimerName() {
    return TASKLIST_TASK_IMPORTER_FINISHED;
  }

  @Override
  protected String reindex() {
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

  @Override
  protected String getMeterReindexTaskTimerName() {
    return TASKLIST_REINDEX_TASK;
  }

  @Override
  protected String getMigratorStepDescription() {
    return TASKLIST_STEP_DESCRIPTION;
  }

  @Override
  protected String getMigratorStepId() {
    return TASKLIST_MIGRATOR_STEP_ID;
  }

  @Override
  protected MigrationRepositoryIndex getMigrationRepositoryIndex() {
    return tasklistMigrationRepositoryIndex;
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
}
