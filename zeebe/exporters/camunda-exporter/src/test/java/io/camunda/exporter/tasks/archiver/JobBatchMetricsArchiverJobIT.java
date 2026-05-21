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
import io.camunda.webapps.schema.descriptors.template.JobMetricsBatchTemplate;
import io.camunda.webapps.schema.entities.jobmetricsbatch.JobMetricsBatchEntity;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestTemplate;

@TestInstance(Lifecycle.PER_CLASS)
public class JobBatchMetricsArchiverJobIT extends ArchiverJobIT<JobBatchMetricsArchiverJob> {
  @Override
  JobBatchMetricsArchiverJob createArchiveJob(
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final ArchiverRepository repository) {
    return new JobBatchMetricsArchiverJob(
        repository,
        resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class),
        exporterMetrics,
        LOGGER,
        executor);
  }

  @TestTemplate
  void shouldArchiveOldJobBatchMetrics(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - a job batch metric with an end time in the past
          final var template =
              resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class);

          final var metric = jobBatchMetric("2020-03-15T10:00:00+00:00");
          store(template, client, metric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, metric, "2020-03-15");
        });
  }

  @TestTemplate
  void shouldNotArchiveRecentJobBatchMetrics(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - one old metric and one recent metric (end time in the future)
          final var template =
              resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class);

          final var oldMetric = jobBatchMetric("2020-03-15T10:00:00+00:00");
          final var recentMetric = jobBatchMetric("2099-01-01T00:00:00+00:00");

          store(template, client, oldMetric);
          store(template, client, recentMetric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - only the old metric should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, oldMetric, "2020-03-15");
          verifyNotMoved(template, client, recentMetric);
        });
  }

  @TestTemplate
  void shouldNotArchiveMetricsFromDifferentPartition(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - an old metric belonging to a different partition
          final var template =
              resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class);

          final var ownPartitionMetric = jobBatchMetric("2020-03-15T10:00:00+00:00");
          final var otherPartitionMetric =
              jobBatchMetric("2020-03-15T10:00:00+00:00").setPartitionId(99);

          store(template, client, ownPartitionMetric);
          store(template, client, otherPartitionMetric);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - only the metric from this partition should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);
          verifyMoved(template, client, ownPartitionMetric, "2020-03-15");
          verifyNotMoved(template, client, otherPartitionMetric);
        });
  }

  @TestTemplate
  void shouldArchiveMultipleOldJobBatchMetrics(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - multiple old metrics from the same date
          final var template =
              resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class);

          final var metric1 = jobBatchMetric("2020-06-01T08:00:00+00:00");
          final var metric2 = jobBatchMetric("2020-06-01T09:00:00+00:00");
          final var metric3 = jobBatchMetric("2020-06-01T10:00:00+00:00");

          store(template, client, metric1);
          store(template, client, metric2);
          store(template, client, metric3);
          client.refresh();

          // when
          final var archived = job.execute();

          // then - all old metrics should be archived
          assertThat(archived).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(3);
          verifyMoved(template, client, metric1, "2020-06-01");
          verifyMoved(template, client, metric2, "2020-06-01");
          verifyMoved(template, client, metric3, "2020-06-01");
        });
  }

  @TestTemplate
  void shouldArchiveMetricsFromDifferentDatesIntoDifferentIndices(
      final ExporterConfiguration config, final SearchClientAdapter client) throws Exception {
    withArchiverJob(
        config,
        (job, resourceProvider) -> {
          // given - old metrics from different dates
          final var template =
              resourceProvider.getIndexTemplateDescriptor(JobMetricsBatchTemplate.class);

          final var metric1 = jobBatchMetric("2020-01-10T12:00:00+00:00");
          final var metric2 = jobBatchMetric("2020-02-20T12:00:00+00:00");

          store(template, client, metric1);
          store(template, client, metric2);
          client.refresh();

          // when - execute twice since batches are grouped by date
          final var firstRun = job.execute();
          assertThat(firstRun).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          client.refresh();
          final var secondRun = job.execute();
          assertThat(secondRun).succeedsWithin(Duration.ofSeconds(5L)).isEqualTo(1);

          // then - metrics should be moved to their respective dated indices
          verifyMoved(template, client, metric1, "2020-01-10");
          verifyMoved(template, client, metric2, "2020-02-20");
        });
  }

  private JobMetricsBatchEntity jobBatchMetric(final String endTime) {
    final var entity = create(JobMetricsBatchEntity::new);
    entity.setPartitionId(PARTITION_ID);
    entity.setStartTime(OffsetDateTime.parse(endTime).minusHours(1));
    entity.setEndTime(OffsetDateTime.parse(endTime));
    entity.setIncompleteBatch(false);
    entity.setTenantId("<default>");
    entity.setJobType("test-job-type");
    entity.setWorker("test-worker");
    entity.setCreatedCount(5);
    entity.setCompletedCount(3);
    entity.setFailedCount(1);
    entity.setLastCreatedAt(OffsetDateTime.parse("2020-01-10T12:00:00+00:00"));
    entity.setLastCompletedAt(OffsetDateTime.parse("2020-01-10T13:00:00+00:00"));
    entity.setLastFailedAt(OffsetDateTime.parse("2020-01-10T14:00:00+00:00"));
    return entity;
  }
}
