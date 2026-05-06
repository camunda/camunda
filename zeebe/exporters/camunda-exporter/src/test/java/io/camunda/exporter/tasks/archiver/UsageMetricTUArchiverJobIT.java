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
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.entities.metrics.UsageMetricsTUEntity;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class UsageMetricTUArchiverJobIT extends ArchiverJobIT<UsageMetricTUArchiverJob> {
  @Override
  UsageMetricTUArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {
    return new UsageMetricTUArchiverJob(
        repository,
        resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class),
        exporterMetrics,
        LOGGER,
        executor);
  }

  @TestTemplate
  void shouldArchiveOldUsageMetricTU(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given
          final var template =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class);

          final var metric = usageMetricTU("2020-01-15T10:00:00+00:00");
          store(template, client, metric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, metric, "2020-01-01");
        });
  }

  @TestTemplate
  void shouldNotArchiveRecentUsageMetricTU(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - one old metric and one recent metric
          final var template =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class);

          final var oldMetric = usageMetricTU("2020-01-15T10:00:00+00:00");
          final var recentMetric = usageMetricTU("2099-01-15T10:00:00+00:00");

          store(template, client, oldMetric);
          store(template, client, recentMetric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - only old metric should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, oldMetric, "2020-01-01");
          verifyNotMoved(template, client, recentMetric);
        });
  }

  @TestTemplate
  void shouldArchiveMultipleUsageMetricTUWithSameEndTime(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - multiple metrics with the same endTime are batched together
          final var template =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class);

          final var metric1 = usageMetricTU("2020-03-05T08:00:00+00:00");
          final var metric2 = usageMetricTU("2020-03-05T08:00:00+00:00");

          store(template, client, metric1);
          store(template, client, metric2);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - both should be archived to the same dated index in a single batch
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(2);
          verifyMoved(template, client, metric1, "2020-03-01");
          verifyMoved(template, client, metric2, "2020-03-01");
        });
  }

  @TestTemplate
  void shouldArchiveMetricTUFromDifferentMonthsInSeparateBatches(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - metrics from different months require separate execute calls
          final var template =
              resourceProvider.getIndexTemplateDescriptor(UsageMetricTUTemplate.class);

          final var januaryMetric = usageMetricTU("2020-01-15T10:00:00+00:00");
          final var marchMetric = usageMetricTU("2020-03-20T14:30:00+00:00");

          store(template, client, januaryMetric);
          store(template, client, marchMetric);
          client.refresh();

          // when - first execution archives the oldest batch
          final var firstBatch = job.execute();

          // then
          assertThat(firstBatch).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, januaryMetric, "2020-01-01");
          verifyNotMoved(template, client, marchMetric);

          // when - second execution archives the next batch
          client.refresh();
          final var secondBatch = job.execute();

          // then
          assertThat(secondBatch).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, marchMetric, "2020-03-01");
        });
  }

  private UsageMetricsTUEntity usageMetricTU(final String startTime) {
    final var entity = create(UsageMetricsTUEntity::new);
    entity.setStartTime(OffsetDateTime.parse(startTime));
    entity.setEndTime(OffsetDateTime.parse(startTime).plusHours(1));
    entity.setAssigneeHash(entity.getId().hashCode());
    entity.setTenantId("<default>");
    entity.setPartitionId(PARTITION_ID);
    return entity;
  }
}
