/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel.Builder;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.entities.JobWorkerStatisticsEntity;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

public final class JobMetricsBatchFixtures extends CommonFixtures {
  public static final OffsetDateTime NOW = OffsetDateTime.now(UTC).truncatedTo(ChronoUnit.MILLIS);
  public static final int PARTITION_ID = 10;

  private JobMetricsBatchFixtures() {}

  public static JobMetricsBatchDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var startTime = NOW.minus(RANDOM.nextInt(100), ChronoUnit.MINUTES);
    final var endTime = startTime.plus(RANDOM.nextInt(10) + 1, ChronoUnit.MINUTES);
    final var lastCreatedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);
    final var lastCompletedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);
    final var lastFailedAt =
        startTime.plus(
            RANDOM.nextInt((int) ChronoUnit.MINUTES.between(startTime, endTime) + 1),
            ChronoUnit.MINUTES);

    final var builder =
        new Builder()
            .key(nextStringKey())
            .partitionId(PARTITION_ID)
            .startTime(startTime)
            .endTime(endTime)
            .tenantId("tenant-" + RANDOM.nextInt(1000))
            .jobType("jobType-" + RANDOM.nextInt(100))
            .worker("worker-" + RANDOM.nextInt(50))
            .createdCount(RANDOM.nextInt(100))
            .lastCreatedAt(lastCreatedAt)
            .completedCount(RANDOM.nextInt(100))
            .lastCompletedAt(lastCompletedAt)
            .failedCount(RANDOM.nextInt(50))
            .lastFailedAt(lastFailedAt)
            .incompleteBatch(RANDOM.nextBoolean());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveMetric(
      final RdbmsWriters rdbmsWriters, final JobMetricsBatchDbModel metric) {
    createAndSaveMetrics(rdbmsWriters, List.of(metric));
  }

  public static void createAndSaveMetrics(
      final RdbmsWriters rdbmsWriters, final List<JobMetricsBatchDbModel> metrics) {
    for (final JobMetricsBatchDbModel metric : metrics) {
      rdbmsWriters.getJobMetricsBatchWriter().create(metric);
    }
    rdbmsWriters.flush();
  }

  /**
   * Asserts that a {@link JobWorkerStatisticsEntity} exactly matches the counts and timestamps of a
   * single {@link JobMetricsBatchDbModel}.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC to handle databases (e.g.
   * MySQL/MariaDB) that return timestamps in local timezone.
   */
  public static void assertWorkerStats(
      final JobWorkerStatisticsEntity stats, final JobMetricsBatchDbModel metric) {
    assertThat(stats.created().count()).isEqualTo(metric.createdCount());
    assertThat(stats.created().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCreatedAt()));
    assertThat(stats.completed().count()).isEqualTo(metric.completedCount());
    assertThat(stats.completed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCompletedAt()));
    assertThat(stats.failed().count()).isEqualTo(metric.failedCount());
    assertThat(stats.failed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastFailedAt()));
  }

  /**
   * Asserts that a {@link JobWorkerStatisticsEntity} matches the aggregated result of a list of
   * {@link JobMetricsBatchDbModel}: counts are summed, timestamps are the MAX across all models.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC.
   */
  public static void assertWorkerStats(
      final JobWorkerStatisticsEntity stats, final List<JobMetricsBatchDbModel> metrics) {
    assertThat(stats.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(stats.created().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCreatedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(stats.completed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCompletedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
    assertThat(stats.failed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastFailedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
  }

  /**
   * Asserts that a {@link JobTypeStatisticsEntity} exactly matches the counts, timestamps and
   * worker count of a single {@link JobMetricsBatchDbModel} (worker count is always 1 for a single
   * model).
   *
   * <p>Timestamps are compared after normalizing both sides to UTC to handle databases (e.g.
   * MySQL/MariaDB) that return timestamps in local timezone.
   */
  public static void assertJobTypeStats(
      final JobTypeStatisticsEntity stats, final JobMetricsBatchDbModel metric) {
    assertThat(stats.created().count()).isEqualTo(metric.createdCount());
    assertThat(stats.created().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCreatedAt()));
    assertThat(stats.completed().count()).isEqualTo(metric.completedCount());
    assertThat(stats.completed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCompletedAt()));
    assertThat(stats.failed().count()).isEqualTo(metric.failedCount());
    assertThat(stats.failed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastFailedAt()));
    assertThat(stats.workers()).isEqualTo(1);
  }

  /**
   * Asserts that a {@link JobTypeStatisticsEntity} matches the aggregated result of a list of
   * {@link JobMetricsBatchDbModel}: counts are summed, timestamps are the MAX, workers is the
   * number of distinct worker names across the list.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC.
   */
  public static void assertJobTypeStats(
      final JobTypeStatisticsEntity stats, final List<JobMetricsBatchDbModel> metrics) {
    assertThat(stats.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(stats.created().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCreatedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(stats.completed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCompletedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
    assertThat(stats.failed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastFailedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.workers())
        .isEqualTo((int) metrics.stream().map(JobMetricsBatchDbModel::worker).distinct().count());
  }

  /**
   * Asserts that a {@link GlobalJobStatisticsEntity} exactly matches the counts, timestamps and
   * incompleteness of a single {@link JobMetricsBatchDbModel}.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC to handle databases (e.g.
   * MySQL/MariaDB) that return timestamps in local timezone.
   */
  public static void assertGlobalStats(
      final GlobalJobStatisticsEntity stats, final JobMetricsBatchDbModel metric) {
    assertThat(stats.created().count()).isEqualTo(metric.createdCount());
    assertThat(stats.created().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCreatedAt()));
    assertThat(stats.completed().count()).isEqualTo(metric.completedCount());
    assertThat(stats.completed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCompletedAt()));
    assertThat(stats.failed().count()).isEqualTo(metric.failedCount());
    assertThat(stats.failed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastFailedAt()));
    assertThat(stats.isIncomplete()).isEqualTo(metric.incompleteBatch());
  }

  /**
   * Asserts that a {@link GlobalJobStatisticsEntity} matches the aggregated result of a list of
   * {@link JobMetricsBatchDbModel}: counts are summed, timestamps are the MAX, and isIncomplete is
   * true if any model in the list has incompleteBatch set.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC.
   */
  public static void assertGlobalStats(
      final GlobalJobStatisticsEntity stats, final List<JobMetricsBatchDbModel> metrics) {
    assertThat(stats.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(stats.created().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCreatedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(stats.completed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCompletedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
    assertThat(stats.failed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastFailedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.isIncomplete())
        .isEqualTo(metrics.stream().anyMatch(JobMetricsBatchDbModel::incompleteBatch));
  }

  /**
   * Asserts that a {@link JobTimeSeriesStatisticsEntity} exactly matches the counts and timestamps
   * of a single {@link JobMetricsBatchDbModel} (single bucket = single row).
   *
   * <p>Timestamps are compared after normalizing both sides to UTC.
   */
  public static void assertTimeSeriesStats(
      final JobTimeSeriesStatisticsEntity stats, final JobMetricsBatchDbModel metric) {
    assertThat(stats.created().count()).isEqualTo(metric.createdCount());
    assertThat(stats.created().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCreatedAt()));
    assertThat(stats.completed().count()).isEqualTo(metric.completedCount());
    assertThat(stats.completed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastCompletedAt()));
    assertThat(stats.failed().count()).isEqualTo(metric.failedCount());
    assertThat(stats.failed().lastUpdatedAt()).isEqualTo(toUtc(metric.lastFailedAt()));
  }

  /**
   * Asserts that a {@link JobTimeSeriesStatisticsEntity} matches the aggregated result of a list of
   * {@link JobMetricsBatchDbModel}: counts are summed, timestamps are the MAX across all models.
   *
   * <p>Timestamps are compared after normalizing both sides to UTC.
   */
  public static void assertTimeSeriesStats(
      final JobTimeSeriesStatisticsEntity stats, final List<JobMetricsBatchDbModel> metrics) {
    assertThat(stats.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(stats.created().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCreatedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(stats.completed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCompletedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
    assertThat(stats.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
    assertThat(stats.failed().lastUpdatedAt())
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastFailedAt)
                .max(OffsetDateTime::compareTo)
                .map(JobMetricsBatchFixtures::toUtc)
                .orElseThrow());
  }

  private static OffsetDateTime toUtc(final OffsetDateTime timestamp) {
    return timestamp == null ? null : timestamp.withOffsetSameInstant(UTC);
  }
}
