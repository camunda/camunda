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
import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.ids;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.commons.configuration.MigrationProperties;
import io.camunda.migration.commons.storage.MigrationRepositoryIndex;
import io.camunda.migration.commons.storage.ProcessorStep;
import io.camunda.migration.commons.utils.ExceptionFilter;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.es.ElasticsearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.os.OpensearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.util.MetricRegistry;
import io.camunda.migration.usagemetric.util.RescheduleTask;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("operate-metric-migrator")
public class OperateMetricMigrator implements Migrator {

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
  private static final Logger LOG = LoggerFactory.getLogger(OperateMetricMigrator.class);
  private final UsageMetricMigrationClient client;
  private final boolean isElasticsearch;
  private final ConnectConfiguration connectConfiguration;
  private final Duration interval;
  private final MetricRegistry metricRegistry;
  private final ScheduledExecutorService scheduler;

  public OperateMetricMigrator(
      final ConnectConfiguration connectConfiguration,
      final MeterRegistry meterRegistry,
      final MigrationProperties properties) {
    this.connectConfiguration = connectConfiguration;
    metricRegistry = new MetricRegistry(meterRegistry);
    isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;
    final var retryConfiguration = properties.getMigrationConfiguration(getClass()).getRetry();
    client =
        isElasticsearch
            ? new ElasticsearchUsageMetricMigrationClient(connectConfiguration, retryConfiguration)
            : new OpensearchUsageMetricMigrationClient(connectConfiguration, retryConfiguration);
    scheduler = Executors.newSingleThreadScheduledExecutor();
    interval = Duration.ofMinutes(1);
  }

  @Override
  public Void call() {

    if (connectConfiguration.getTypeEnum() != DatabaseType.ELASTICSEARCH
        && connectConfiguration.getTypeEnum() != DatabaseType.OPENSEARCH) {
      LOG.info(
          "Unsupported database type: {}, skipping migration", connectConfiguration.getTypeEnum());
      return null;
    }

    final var indexDescriptors =
        new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
    final var metricIndex = indexDescriptors.get(MetricIndex.class);
    final var usageMetricIndex = indexDescriptors.get(UsageMetricIndex.class);
    final var migrationRepositoryIndex =
        new MigrationRepositoryIndex(connectConfiguration.getIndexPrefix(), isElasticsearch);

    // check if migration already applied
    ProcessorStep migrationStep = null;
    try {
      migrationStep =
          client.findOne(
              migrationRepositoryIndex.getFullQualifiedName(),
              createOperateMigratorStepSearchQuery(),
              ProcessorStep.class);
    } catch (final MigrationException e) {
      if (ExceptionFilter.shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
    }
    if (migrationStep != null && migrationStep.isApplied()) {
      LOG.info("Skipping operate metric migration, already applied: {}", migrationStep);
      return null;
    }

    // don't reindex metrics older than two years
    final var minEventTime = OffsetDateTime.now().minusYears(2);

    final var searchQuery =
        createOperateMetricSearchQuery(
            List.of(EVENT_PROCESS_INSTANCE_STARTED, EVENT_DECISION_INSTANCE_EVALUATED),
            minEventTime);
    LOG.info("Migrating operate-metric index with search query: {}", searchQuery);

    try {
      final var taskId =
          client.reindex(
              metricIndex.getFullQualifiedName(),
              usageMetricIndex.getFullQualifiedName(),
              searchQuery,
              SCRIPT);

      LOG.info("Waiting for taskId {} completion", taskId);
      // return null so wait continues
      final boolean completed =
          Boolean.TRUE.equals(
              metricRegistry.measureOperateReindexTask(
                  () ->
                      waitForCallback(
                              () -> {
                                final var res = client.getTask(taskId);
                                return res.completed()
                                    ? true
                                    : null; // return null so wait continues
                              },
                              () -> false,
                              interval.getSeconds())
                          .join()));

      LOG.info(
          "Updating migration step for operate-metric index with taskId {} and result {}",
          taskId,
          completed);
      client.persistMigratorStep(
          migrationRepositoryIndex.getFullQualifiedName(),
          OPERATE_MIGRATOR_STEP_ID,
          taskId,
          OPERATE_STEP_DESCRIPTION,
          completed);

    } catch (final Exception e) {
      LOG.error("Failed to reindex operate-metric index", e);
    }

    return null;
  }

  private <T> CompletableFuture<T> waitForCallback(
      final Supplier<T> fnCallback, final Supplier<T> fnTimeout, final long maxWaitSeconds) {
    final var future = new CompletableFuture<T>();

    final var task =
        new RescheduleTask<T>(scheduler, fnCallback, fnTimeout, maxWaitSeconds, future);
    scheduler.schedule(task, 0, TimeUnit.SECONDS);

    return future;
  }

  public SearchQuery createOperateMetricSearchQuery(
      final List<String> events, final OffsetDateTime minEventTime) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(
        SearchQueryBuilders.stringOperations(MetricIndex.EVENT, List.of(Operation.in(events))));
    queries.addAll(
        SearchQueryBuilders.dateTimeOperations(
            MetricIndex.EVENT_TIME, List.of(Operation.gt(minEventTime))));
    return and(queries);
  }

  public SearchQuery createOperateMigratorStepSearchQuery() {
    return ids(OPERATE_MIGRATOR_STEP_ID);
  }
}
