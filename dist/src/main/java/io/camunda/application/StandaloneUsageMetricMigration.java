/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.not;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.application.commons.migration.metric.ElasticsearchUsageMetricMigrationClient;
import io.camunda.application.commons.migration.metric.OpensearchUsageMetricMigrationClient;
import io.camunda.application.commons.migration.metric.UsageMetricMigrationClient;
import io.camunda.application.commons.search.SearchEngineDatabaseConfiguration;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.operate.store.MetricsStore;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.filter.Operation;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.descriptors.index.UsageMetricIndex;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class StandaloneUsageMetricMigration implements CommandLineRunner {

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
  private static final Logger LOG = LoggerFactory.getLogger(StandaloneUsageMetricMigration.class);
  private final ConnectConfiguration connectConfiguration;

  public StandaloneUsageMetricMigration(final ConnectConfiguration connectConfiguration) {
    this.connectConfiguration = connectConfiguration;
  }

  public static void main(final String[] args) throws IOException {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    final SpringApplication application =
        new SpringApplicationBuilder()
            .logStartupInfo(true)
            .web(WebApplicationType.NONE)
            .sources(
                StandaloneUsageMetricMigration.class,
                SearchEngineDatabaseConfiguration.class,
                UnifiedConfiguration.class,
                TasklistPropertiesOverride.class,
                OperatePropertiesOverride.class)
            .addCommandLineProperties(true)
            .build(args);

    application.run(args);

    System.exit(0);
  }

  private static UsageMetricMigrationClient getMigrationClient(
      final ConnectConfiguration connectConfiguration) {
    if (connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH) {
      return new ElasticsearchUsageMetricMigrationClient(
          new ElasticsearchConnector(connectConfiguration).createClient(),
          new ElasticsearchTransformers());
    } else if (connectConfiguration.getTypeEnum() == DatabaseType.OPENSEARCH) {
      return new OpensearchUsageMetricMigrationClient(
          new OpensearchConnector(connectConfiguration).createClient(),
          new OpensearchTransformers());
    } else {
      throw new IllegalArgumentException(
          "Unsupported database type: " + connectConfiguration.getTypeEnum());
    }
  }

  @Override
  public void run(final String... args) throws Exception {
    final var isElasticsearch = connectConfiguration.getTypeEnum() == DatabaseType.ELASTICSEARCH;
    final var indexDescriptors =
        new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch);
    final var migrationClient = getMigrationClient(connectConfiguration);
    final var metricIndex = indexDescriptors.get(MetricIndex.class);
    final var usageMetricIndex = indexDescriptors.get(UsageMetricIndex.class);

    // only reindex documents that are older than the first usage metric entity
    final var firstUsageMetric =
        waitForUsageMetricExport(
                migrationClient,
                usageMetricIndex,
                Duration.ofMinutes(1),
                () -> {
                  try {
                    return migrationClient.getFirstUsageMetricEntity(
                        usageMetricIndex.getFullQualifiedName(),
                        not(term(UsageMetricIndex.PARTITION_ID, -1)));
                  } catch (final IOException e) {
                    LOG.error("Failed to fetch usage metric entity", e);
                    return null;
                  }
                })
            .join();
    if (firstUsageMetric == null) {
      LOG.info(
          "No usage metric entity found in index {}, skipping migration",
          usageMetricIndex.getFullQualifiedName());
      return;
    }

    // check if there are already migrated metrics and select latest event time
    final var latestMigratedMetric =
        waitForUsageMetricExport(
                migrationClient,
                usageMetricIndex,
                Duration.ofMinutes(1),
                () -> {
                  try {
                    return migrationClient.getLatestMigratedEntity(
                        usageMetricIndex.getFullQualifiedName(),
                        term(UsageMetricIndex.PARTITION_ID, -1));
                  } catch (final IOException e) {
                    LOG.error("Failed to fetch usage metric entity", e);
                    return null;
                  }
                })
            .join();
    if (latestMigratedMetric != null) {
      LOG.info("Latest migrated metric found: {}", latestMigratedMetric);
    }

    // operate metrics export could be slightly ahead so we subtract one minute to avoid duplicates
    final var maxEventTime = firstUsageMetric.getEventTime().minusMinutes(1);
    final var minEventTime =
        latestMigratedMetric != null
            ? latestMigratedMetric.getEventTime()
            : firstUsageMetric.getEventTime().minusYears(2);
    migrateOperateMIndex(
        migrationClient, usageMetricIndex, metricIndex, minEventTime, maxEventTime);
  }

  private CompletableFuture<UsageMetricsEntity> waitForUsageMetricExport(
      final UsageMetricMigrationClient migrationClient,
      final UsageMetricIndex usageMetricIndex,
      final Duration waitDuration,
      final Supplier<UsageMetricsEntity> fnGetEntity) {

    LOG.info(
        "Fetching first usage metric entity from index {}",
        usageMetricIndex.getFullQualifiedName());
    final var timeout = Instant.now().plusSeconds(waitDuration.getSeconds());
    final CompletableFuture<UsageMetricsEntity> future = new CompletableFuture<>();

    try (final var scheduler = Executors.newScheduledThreadPool(1)) {
      scheduler.scheduleAtFixedRate(
          () -> {
            final var usageMetricEntity = fnGetEntity.get();
            if (usageMetricEntity != null) {
              future.complete(usageMetricEntity);
              scheduler.shutdown();
            }

            if (Instant.now().isBefore(timeout)) {
              future.complete(null);
              scheduler.shutdown();
            }
          },
          0,
          5,
          TimeUnit.SECONDS);
    }

    return future;
  }

  private void migrateOperateMIndex(
      final UsageMetricMigrationClient migrationClient,
      final UsageMetricIndex usageMetricIndex,
      final MetricIndex metricIndex,
      final OffsetDateTime minEventTime,
      final OffsetDateTime maxEventTime) {

    final var searchQuery =
        createOperateMetricSearchQuery(
            List.of(
                MetricsStore.EVENT_PROCESS_INSTANCE_STARTED,
                MetricsStore.EVENT_DECISION_INSTANCE_EVALUATED),
            minEventTime,
            maxEventTime);

    LOG.info("Migrating operate-metric index with search query: {}", searchQuery);

    final var result =
        migrationClient.reindex(
            metricIndex.getFullQualifiedName(),
            usageMetricIndex.getFullQualifiedName(),
            searchQuery,
            SCRIPT);

    if (!result.successful()) {
      LOG.error(
          "Failed to migrate {} to {} due to {}",
          result.source(),
          result.destination(),
          result.message());
    } else {
      LOG.info("Finished migrating operate-metric index");
    }
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
}
