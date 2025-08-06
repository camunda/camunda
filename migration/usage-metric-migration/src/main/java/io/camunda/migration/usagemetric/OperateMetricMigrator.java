/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.usagemetric;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.api.Migrator;
import io.camunda.migration.usagemetric.client.MigrationRepositoryIndex;
import io.camunda.migration.usagemetric.client.MigrationStep;
import io.camunda.migration.usagemetric.client.UsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.es.ElasticsearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.client.os.OpensearchUsageMetricMigrationClient;
import io.camunda.migration.usagemetric.util.MetricRegistry;
import io.camunda.migration.usagemetric.util.RescheduleTask;
import io.camunda.operate.store.MetricsStore;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("operate-metric-migrator")
public class OperateMetricMigrator implements Migrator {

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
              MetricsStore.EVENT_PROCESS_INSTANCE_STARTED,
              UsageMetricsEventType.RPI,
              MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED,
              UsageMetricsEventType.EDI);
  public static final Pattern MIGRATION_REPOSITORY_NOT_EXISTS =
      Pattern.compile(
          "no such index \\[[a-zA-Z0-9\\-]+-migration-steps-repository-[0-9]+\\.[0-9]+\\.[0-9]+_]");
  private static final Logger LOG = LoggerFactory.getLogger(OperateMetricMigrator.class);
  private final UsageMetricMigrationClient client;
  private final boolean isElasticsearch;
  private final ConnectConfiguration connectConfiguration;
  private final Duration interval;
  private final MetricRegistry metricRegistry;
  private final ScheduledExecutorService scheduler;

  public OperateMetricMigrator(
      final ConnectConfiguration connectConfiguration, final MeterRegistry meterRegistry) {
    this.connectConfiguration = connectConfiguration;
    metricRegistry = new MetricRegistry(meterRegistry);
    isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;
    client =
        isElasticsearch
            ? new ElasticsearchUsageMetricMigrationClient(connectConfiguration)
            : new OpensearchUsageMetricMigrationClient(connectConfiguration);
    scheduler = Executors.newScheduledThreadPool(1);
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
    MigrationStep migrationStep = null;
    try {
      migrationStep =
          client.readOperateMetricMigratorStep(migrationRepositoryIndex.getFullQualifiedName());
    } catch (final MigrationException e) {
      if (shouldThrowException(e)) {
        throw new MigrationException(e.getMessage(), e);
      }
    }
    if (migrationStep != null && migrationStep.applied()) {
      LOG.info("Skipping operate metric migration, already applied: {}", migrationStep);
      return null;
    }

    // only reindex documents that are older than the first usage metric entity
    final var firstUsageMetric = getFirstZeebeMetric(usageMetricIndex);

    // only reindex documents that are newer than the latest migrated metric
    final var latestMigratedMetric = getLatestMigratedMetric(usageMetricIndex);

    // operate metrics export could be slightly ahead so we subtract one minute to avoid duplicates
    final var maxEventTime =
        firstUsageMetric == null
            ? OffsetDateTime.now()
            : firstUsageMetric.getEventTime().minusMinutes(1);
    // don't reindex metrics older than two years
    final var minEventTime =
        latestMigratedMetric != null
            ? latestMigratedMetric.getEventTime()
            : maxEventTime.minusYears(2);

    final var searchQuery =
        createOperateMetricSearchQuery(
            List.of(
                MetricsStore.EVENT_PROCESS_INSTANCE_STARTED,
                MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED),
            minEventTime,
            maxEventTime);
    LOG.info("Migrating operate-metric index with search query: {}", searchQuery);

    try {
      final var taskId =
          client.reindex(
              metricIndex.getFullQualifiedName(),
              usageMetricIndex.getFullQualifiedName(),
              searchQuery,
              SCRIPT);

      LOG.info("Waiting for taskId {} completion", taskId);
      final boolean completed =
          metricRegistry.measureOperateReindexTask(
              () ->
                  waitForCallback(
                          () -> {
                            final var res = client.getTask(taskId);
                            return res ? true : null; // return null so wait continues
                          },
                          () -> false,
                          interval.getSeconds())
                      .join());

      LOG.info(
          "Updating migration step for operate-metric index with taskId {} and result {}",
          taskId,
          completed);
      client.writeOperateMetricMigratorStep(
          migrationRepositoryIndex.getFullQualifiedName(), taskId, completed);

    } catch (final Exception e) {
      LOG.error("Failed to reindex operate-metric index", e);
    }

    return null;
  }

  private UsageMetricsEntity getLatestMigratedMetric(final UsageMetricIndex usageMetricIndex) {
    try {
      final var latestMigratedMetric =
          client.getLatestMigratedEntity(
              usageMetricIndex.getFullQualifiedName(), term(UsageMetricIndex.PARTITION_ID, -1));
      if (latestMigratedMetric != null) {
        LOG.info("Latest migrated metric found: {}", latestMigratedMetric);
      }
      return latestMigratedMetric;
    } catch (final IOException e) {
      LOG.error("Failed to fetch usage metric entity", e);
      return null;
    }
  }

  private UsageMetricsEntity getFirstZeebeMetric(final UsageMetricIndex usageMetricIndex) {
    try {
      final var firstUsageMetric =
          client.getFirstUsageMetricEntity(
              usageMetricIndex.getFullQualifiedName(),
              not(term(UsageMetricIndex.PARTITION_ID, -1)));
      if (firstUsageMetric == null) {
        LOG.info(
            "No usage metric entity found in index {}, using current time as max event time",
            usageMetricIndex.getFullQualifiedName());
      }
      return firstUsageMetric;
    } catch (final IOException e) {
      LOG.error("Failed to fetch usage metric entity", e);
      return null;
    }
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
      final List<String> events,
      final OffsetDateTime minEventTime,
      final OffsetDateTime maxEventTime) {
    final var queries = new ArrayList<SearchQuery>();
    queries.addAll(
        SearchQueryBuilders.stringOperations(MetricIndex.EVENT, List.of(Operation.in(events))));
    queries.addAll(
        SearchQueryBuilders.dateTimeOperations(
            MetricIndex.EVENT_TIME,
            List.of(Operation.gt(minEventTime), Operation.lt(maxEventTime))));
    return and(queries);
  }

  /**
   * Check if the exception should be rethrown or not. Throwing the exception on this stage will
   * cause the Spring Boot application to terminate. Some exceptions can be expected when dealing
   * with Greenfield deployments and these should be ignored.
   *
   * @param exception the exception to check
   * @return true if the exception should be rethrown, false otherwise
   */
  private boolean shouldThrowException(final Exception exception) {
    if (exception.getCause() instanceof final ElasticsearchException ex) {
      return ex.error().reason() != null
          && !MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    } else if (exception.getCause() instanceof final OpenSearchException ex) {
      return ex.error().reason() != null
          && !MIGRATION_REPOSITORY_NOT_EXISTS.matcher(ex.error().reason()).find();
    }
    return true;
  }
}
