/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.SearchClientAdapter;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEntity;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsEventType;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class UsageMetricArchiverJobIT extends ArchiverJobIT<UsageMetricArchiverJob> {
  @Override
  UsageMetricArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {
    return new UsageMetricArchiverJob(
        repository,
        resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class),
        exporterMetrics,
        LOGGER,
        executor);
  }

  @TestTemplate
  void shouldArchiveOldUsageMetric(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var usageMetricTemplate =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class);

          final var metric = usageMetric("2020-01-15T10:00:00+00:00");
          store(usageMetricTemplate, client, metric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(usageMetricTemplate, client, metric, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldNotArchiveRecentUsageMetric(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - one old metric and one recent metric
          final var usageMetricTemplate =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class);

          final var oldMetric = usageMetric("2020-01-15T10:00:00+00:00");
          final var recentMetric = usageMetric("2099-01-15T10:00:00+00:00");

          store(usageMetricTemplate, client, oldMetric);
          store(usageMetricTemplate, client, recentMetric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - only old metric should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(usageMetricTemplate, client, oldMetric, "2020-01-01");
          verifyNotMoved(usageMetricTemplate, client, recentMetric);
        });
  }

  @TestTemplate
  void shouldArchiveMultipleUsageMetricsWithSameEndTime(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - multiple metrics with the same endTime are batched together
          final var usageMetricTemplate =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class);

          final var metric1 = usageMetric("2020-03-05T08:00:00+00:00");
          final var metric2 = usageMetric("2020-03-05T08:00:00+00:00");

          store(usageMetricTemplate, client, metric1);
          store(usageMetricTemplate, client, metric2);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - both should be archived to the same dated index in a single batch
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(2);
          verifyMoved(usageMetricTemplate, client, metric1, "2020-03-01");
          verifyMoved(usageMetricTemplate, client, metric2, "2020-03-01");
        });
  }

  @TestTemplate
  void shouldArchiveMetricsFromDifferentMonthsInSeparateBatches(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - metrics from different months require separate execute calls
          final var usageMetricTemplate =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTemplate.class);

          final var januaryMetric = usageMetric("2020-01-15T10:00:00+00:00");
          final var marchMetric = usageMetric("2020-03-20T14:30:00+00:00");

          store(usageMetricTemplate, client, januaryMetric);
          store(usageMetricTemplate, client, marchMetric);
          client.refresh();

          // when - first execution archives the oldest batch
          final var firstBatch = job.execute();

          // then
          assertThat(firstBatch).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(usageMetricTemplate, client, januaryMetric, "2020-01-01");
          verifyNotMoved(usageMetricTemplate, client, marchMetric);

          // when - second execution archives the next batch
          client.refresh(); // refresh so we don't try to move the same batch
          final var secondBatch = job.execute();

          // then
          assertThat(secondBatch).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(usageMetricTemplate, client, marchMetric, "2020-03-01");
        });
  }

  private UsageMetricsEntity usageMetric(final String startTime) {
    final var entity = create(UsageMetricsEntity::new);
    entity.setStartTime(OffsetDateTime.parse(startTime));
    entity.setEndTime(OffsetDateTime.parse(startTime).plusHours(1));
    entity.setEventType(UsageMetricsEventType.RPI);
    entity.setEventValue(1L);
    entity.setTenantId("<default>");
    entity.setPartitionId(PARTITION_ID);
    return entity;
  }
}
