/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric;

import static io.camunda.migration.usagemetric.client.UsageMetricMigrationClient.OPERATE_MIGRATOR_STEP_ID;
import static io.camunda.migration.usagemetric.client.UsageMetricMigrationClient.OPERATE_STEP_DESCRIPTION;
import static io.camunda.migration.usagemetric.util.MetricRegistry.OPERATE_REINDEX_TASK;
import static io.camunda.migration.usagemetric.util.MetricRegistry.OPERATE_TASK_IMPORTER_FINISHED;
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.wildcardQuery;

import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.index.ImportPositionIndex;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("operate-metric-migrator")
public class OperateMetricMigrator extends MetricMigrator {

  public static final String EVENT_PROCESS_INSTANCE_STARTED = "EVENT_PROCESS_INSTANCE_STARTED";
  public static final String EVENT_DECISION_INSTANCE_EVALUATED =
      "EVENT_DECISION_INSTANCE_EVALUATED";

  public static final String SCRIPT =
      """
      String value = ctx._source.remove("value");
      ctx._id = value;
      ctx._source.id = value;
      ctx._source.eventValue = 1;
      ctx._source.partitionId = -1;
      String event = ctx._source.remove("event");
      if (event == "%s") {
        ctx._source.eventType = "%s";
      } else if (event == "%s") {
        ctx._source.eventType = "%s";
      }
      """
          .formatted(
              EVENT_PROCESS_INSTANCE_STARTED,
              UsageMetricsEventType.RPI,
              EVENT_DECISION_INSTANCE_EVALUATED,
              UsageMetricsEventType.EDI);
  private final MigrationRepositoryIndex migrationRepositoryIndex;
  private final ImportPositionIndex importPositionIndex;

  public OperateMetricMigrator(
      final ConnectConfiguration connectConfiguration,
      final MeterRegistry meterRegistry,
      final MigrationProperties properties) {
    super(connectConfiguration, meterRegistry, properties);
    migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
    importPositionIndex =
        new ImportPositionIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);
  }

  @Override
  protected String getShortName() {
    return "RPI/EDI";
  }

  @Override
  protected IndexDescriptor getImportPositionIndex() {
    return importPositionIndex;
  }

  @Override
  protected SearchQuery getImportPositionQuery() {
    return or(
        wildcardQuery(ImportPositionIndex.ID, "*-" + VariableTemplate.INDEX_NAME),
        // We don't have a reference for the `process-instance` index name, so use it directly
        // as it's one of the streams that populate the `operate-list-view` index and
        // therefore should wait for it to be completed
        wildcardQuery(ImportPositionIndex.ID, "*-process-instance"),
        wildcardQuery(ImportPositionIndex.ID, "*-" + DecisionInstanceTemplate.INDEX_NAME));
  }

  @Override
  protected String getMeterImporterFinishedTimerName() {
    return OPERATE_TASK_IMPORTER_FINISHED;
  }

  @Override
  protected String reindex() {
    final var minEventTime = OffsetDateTime.now().minusYears(2);
    final var searchQuery =
        searchQuery(
            List.of(EVENT_PROCESS_INSTANCE_STARTED, EVENT_DECISION_INSTANCE_EVALUATED),
            minEventTime);
    return client.reindex(
        indexDescriptors.get(MetricIndex.class).getFullQualifiedName(),
        indexDescriptors.get(UsageMetricIndex.class).getFullQualifiedName(),
        searchQuery,
        SCRIPT);
  }

  @Override
  protected String getMeterReindexTaskTimerName() {
    return OPERATE_REINDEX_TASK;
  }

  @Override
  protected String getMigratorStepDescription() {
    return OPERATE_STEP_DESCRIPTION;
  }

  @Override
  protected String getMigratorStepId() {
    return OPERATE_MIGRATOR_STEP_ID;
  }

  @Override
  protected MigrationRepositoryIndex getMigrationRepositoryIndex() {
    return migrationRepositoryIndex;
  }

  public SearchQuery searchQuery(final List<String> events, final OffsetDateTime minEventTime) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(
        SearchQueryBuilders.stringOperations(MetricIndex.EVENT, List.of(Operation.in(events))));
    queries.addAll(
        SearchQueryBuilders.dateTimeOperations(
            MetricIndex.EVENT_TIME, List.of(Operation.gt(minEventTime))));
    return and(queries);
  }
}
