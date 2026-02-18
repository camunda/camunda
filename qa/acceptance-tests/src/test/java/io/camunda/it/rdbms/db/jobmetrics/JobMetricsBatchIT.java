/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.jobmetrics;

import static io.camunda.it.rdbms.db.fixtures.JobMetricsBatchFixtures.NOW;
import static io.camunda.it.rdbms.db.fixtures.JobMetricsBatchFixtures.PARTITION_ID;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.JobMetricsBatchDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.service.JobMetricsBatchWriter;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.fixtures.JobMetricsBatchFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class JobMetricsBatchIT {

  private static final String TENANT1 = "tenant1";
  private static final String TENANT2 = "tenant2";
  private static final String JOB_TYPE_A = "jobTypeA";
  private static final String JOB_TYPE_B = "jobTypeB";
  private static final String WORKER_1 = "worker1";
  private static final String WORKER_2 = "worker2";

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriters rdbmsWriters;
  private JobMetricsBatchDbReader jobMetricsBatchReader;
  private JobMetricsBatchWriter jobMetricsBatchWriter;

  /**
   * Normalizes an OffsetDateTime to UTC for comparison. MySQL/MariaDB return timestamps in local
   * timezone even though we store them as UTC, so we need to convert for proper comparison.
   */
  private OffsetDateTime toUtc(final OffsetDateTime timestamp) {
    return timestamp == null ? null : timestamp.withOffsetSameInstant(UTC);
  }

  /**
   * Normalizes a JobTypeStatisticsEntity to have all timestamps in UTC for comparison across
   * different database types.
   */
  private JobTypeStatisticsEntity normalizeToUtc(final JobTypeStatisticsEntity entity) {
    return new JobTypeStatisticsEntity(
        entity.jobType(),
        new StatusMetric(entity.created().count(), toUtc(entity.created().lastUpdatedAt())),
        new StatusMetric(entity.completed().count(), toUtc(entity.completed().lastUpdatedAt())),
        new StatusMetric(entity.failed().count(), toUtc(entity.failed().lastUpdatedAt())),
        entity.workers());
  }

  private OffsetDateTime getMinStartTime(final List<JobMetricsBatchDbModel> metrics) {
    return metrics.stream()
        .map(JobMetricsBatchDbModel::startTime)
        .min(OffsetDateTime::compareTo)
        .orElseThrow();
  }

  private OffsetDateTime getMaxEndTime(final List<JobMetricsBatchDbModel> metrics) {
    return metrics.stream()
        .map(JobMetricsBatchDbModel::endTime)
        .max(OffsetDateTime::compareTo)
        .orElseThrow();
  }

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    jobMetricsBatchReader = rdbmsService.getJobMetricsBatchDbReader();
    jobMetricsBatchWriter = rdbmsWriters.getJobMetricsBatchWriter();
  }

  @AfterEach
  void tearDown() {
    // Note: If you add other partition ids in your tests, make sure to clean them up here as well
    jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.plusDays(100), Integer.MAX_VALUE);
  }

  @TestTemplate
  public void shouldAggregateGlobalJobStatistics() {
    // given
    final var metric1 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).incompleteBatch(false));

    final var metric2 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).incompleteBatch(false));

    final List<JobMetricsBatchDbModel> metrics = List.of(metric1, metric2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - verify aggregation of both metrics
    assertThat(actual.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(toUtc(actual.created().lastUpdatedAt()))
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCreatedAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow());
    assertThat(actual.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(toUtc(actual.completed().lastUpdatedAt()))
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastCompletedAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow());
    assertThat(actual.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
    assertThat(toUtc(actual.failed().lastUpdatedAt()))
        .isEqualTo(
            metrics.stream()
                .map(JobMetricsBatchDbModel::lastFailedAt)
                .max(OffsetDateTime::compareTo)
                .orElseThrow());
    assertThat(actual.isIncomplete()).isFalse();
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleTenants() {
    // given
    final var metricTenant1 = JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1));

    final var metricTenant2 = JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT2));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricTenant1, metricTenant2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - verify aggregation across tenants
    assertThat(actual.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(actual.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(actual.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleJobTypes() {
    // given
    final var metricJobTypeA = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_A));

    final var metricJobTypeB = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_B));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricJobTypeA, metricJobTypeB);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - verify aggregation across job types
    assertThat(actual.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(actual.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(actual.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleWorkers() {
    // given
    final var metricWorker1 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).worker(WORKER_1));

    final var metricWorker2 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).worker(WORKER_2));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricWorker1, metricWorker2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - verify aggregation across workers
    assertThat(actual.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(actual.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(actual.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());
  }

  @TestTemplate
  public void shouldFilterMetricsByTimeRange() {
    // given
    final var metricOld =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.startTime(NOW.minusMinutes(100)).endTime(NOW.minusMinutes(50)));
    final var metricMiddle =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.startTime(NOW.minusMinutes(30)).endTime(NOW.minusMinutes(20)));
    final var metricRecent =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.startTime(NOW.minusMinutes(10)).endTime(NOW));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricOld, metricMiddle, metricRecent);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter to only get middle batch
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metricMiddle.startTime().minusSeconds(1))
                                .to(metricMiddle.endTime().plusSeconds(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only middle batch data
    assertThat(actual.created().count()).isEqualTo(metricMiddle.createdCount());
    assertThat(actual.completed().count()).isEqualTo(metricMiddle.completedCount());
    assertThat(actual.failed().count()).isEqualTo(metricMiddle.failedCount());
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(metricMiddle.lastCreatedAt());
    assertThat(toUtc(actual.completed().lastUpdatedAt())).isEqualTo(metricMiddle.lastCompletedAt());
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(metricMiddle.lastFailedAt());
  }

  @TestTemplate
  public void shouldFilterMetricsByJobType() {
    // given
    final var metricJobTypeA =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A));

    final var metricJobTypeB =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).jobType(JOB_TYPE_B));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricJobTypeA, metricJobTypeB);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter by JOB_TYPE_A only
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1))
                                .jobType(JOB_TYPE_A))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only JOB_TYPE_A data
    assertThat(actual.created().count()).isEqualTo(metricJobTypeA.createdCount());
    assertThat(actual.completed().count()).isEqualTo(metricJobTypeA.completedCount());
    assertThat(actual.failed().count()).isEqualTo(metricJobTypeA.failedCount());
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(metricJobTypeA.lastCreatedAt());
    assertThat(toUtc(actual.completed().lastUpdatedAt()))
        .isEqualTo(metricJobTypeA.lastCompletedAt());
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(metricJobTypeA.lastFailedAt());
  }

  @TestTemplate
  public void shouldReturnMaxLastUpdatedAtTimestamp() {
    // given
    final var metric1 = JobMetricsBatchFixtures.createRandomized(b -> b);
    final var metric2 = JobMetricsBatchFixtures.createRandomized(b -> b);

    final List<JobMetricsBatchDbModel> metrics = List.of(metric1, metric2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - should return max timestamps
    final var expectedMaxCreatedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastCreatedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();
    final var expectedMaxCompletedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastCompletedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();
    final var expectedMaxFailedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastFailedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();

    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(expectedMaxCreatedAt);
    assertThat(toUtc(actual.completed().lastUpdatedAt())).isEqualTo(expectedMaxCompletedAt);
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(expectedMaxFailedAt);
  }

  @TestTemplate
  public void shouldReturnIncompleteWhenAnyBatchIsIncomplete() {
    // given
    final var metricComplete =
        JobMetricsBatchFixtures.createRandomized(b -> b.incompleteBatch(false));

    final var metricIncomplete =
        JobMetricsBatchFixtures.createRandomized(b -> b.incompleteBatch(true));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricComplete, metricIncomplete);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.isIncomplete()).isTrue();
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoMetricsExist() {
    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW.minusMinutes(15)).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual)
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                false));
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoMetricsMatchFilter() {
    // given
    final var metric = JobMetricsBatchFixtures.createRandomized(b -> b);
    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);

    // when - query for a different time range
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(q -> q.filter(f -> f.from(NOW).to(NOW.plusMinutes(10)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual)
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                false));
  }

  @TestTemplate
  public void shouldFilterByAuthorizedTenants() {
    // given
    final var metricTenant1 = JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1));

    final var metricTenant2 = JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT2));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricTenant1, metricTenant2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - only authorize TENANT1
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(
                AuthorizationCheck.disabled(), TenantCheck.enabled(List.of(TENANT1))));

    // then - only TENANT1 data
    assertThat(actual.created().count()).isEqualTo(metricTenant1.createdCount());
    assertThat(actual.completed().count()).isEqualTo(metricTenant1.completedCount());
    assertThat(actual.failed().count()).isEqualTo(metricTenant1.failedCount());
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoAuthorizedTenants() {
    // given
    final var metric = JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1));

    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);

    // when - authorize no tenants
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metric.startTime().minusMinutes(1))
                                .to(metric.endTime().plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of())));

    // then
    assertThat(actual)
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                false));
  }

  @TestTemplate
  public void shouldHandleMetricsWithNullTimestamps() {
    // given - metrics with some zero counts (null timestamps)
    final var metric =
        JobMetricsBatchFixtures.createRandomized(
            b ->
                b.tenantId(TENANT1)
                    .completedCount(0)
                    .lastCompletedAt(null)
                    .failedCount(0)
                    .lastFailedAt(null));

    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metric.startTime().minusMinutes(1))
                                .to(metric.endTime().plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.created().count()).isEqualTo(metric.createdCount());
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(metric.lastCreatedAt());
    assertThat(actual.completed().count()).isZero();
    assertThat(actual.completed().lastUpdatedAt()).isNull();
    assertThat(actual.failed().count()).isZero();
    assertThat(actual.failed().lastUpdatedAt()).isNull();
  }

  @TestTemplate
  public void shouldAsyncWriteAndReadMetrics() {
    // given
    final var metric = JobMetricsBatchFixtures.createRandomized(b -> b);

    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);
    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metric.startTime().minusMinutes(1))
                                .to(metric.endTime().plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.created().count()).isEqualTo(metric.createdCount());
    assertThat(actual.completed().count()).isEqualTo(metric.completedCount());
    assertThat(actual.failed().count()).isEqualTo(metric.failedCount());
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatch() {
    // given
    final var metric = JobMetricsBatchFixtures.createRandomized(b -> b);
    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);
    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metric.startTime().minusMinutes(1))
                                .to(metric.endTime().plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.created().count()).isEqualTo(metric.createdCount());
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(metric.lastCreatedAt());
    assertThat(actual.completed().count()).isEqualTo(metric.completedCount());
    assertThat(toUtc(actual.completed().lastUpdatedAt())).isEqualTo(metric.lastCompletedAt());
    assertThat(actual.failed().count()).isEqualTo(metric.failedCount());
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(metric.lastFailedAt());
    assertThat(actual.isIncomplete()).isEqualTo(metric.incompleteBatch());
  }

  @TestTemplate
  public void shouldInsertMultipleJobMetricsBatches() {
    // given
    final var metric1 =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A).worker(WORKER_1));

    final var metric2 =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_B).worker(WORKER_2));

    final var metric3 =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT2).jobType(JOB_TYPE_A).worker(WORKER_1).incompleteBatch(true));

    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, List.of(metric1, metric2, metric3));

    // when - then
    assertThat(rdbmsWriters).isNotNull();
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithDifferentTenants() {
    // given
    final var metricTenant1 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A));

    final var metricTenant2 =
        JobMetricsBatchFixtures.createRandomized(b -> b.tenantId(TENANT2).jobType(JOB_TYPE_A));

    final var metrics = List.of(metricTenant1, metricTenant2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // then - verify both were written successfully
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));
    assertThat(actual.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithIncompleteBatch() {
    // given
    final var metric = JobMetricsBatchFixtures.createRandomized(b -> b.incompleteBatch(true));
    JobMetricsBatchFixtures.createAndSaveMetric(rdbmsWriters, metric);

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metric.startTime().minusMinutes(1))
                                .to(metric.endTime().plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.isIncomplete()).isTrue();
    assertThat(actual.created().count()).isEqualTo(metric.createdCount());
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(metric.lastCreatedAt());
    assertThat(actual.completed().count()).isEqualTo(metric.completedCount());
    assertThat(toUtc(actual.completed().lastUpdatedAt())).isEqualTo(metric.lastCompletedAt());
    assertThat(actual.failed().count()).isEqualTo(metric.failedCount());
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(metric.lastFailedAt());
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithNullLastUpdatedTimes() {
    // given - create a batch with zero counts, so no lastUpdatedAt times
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(0)
            .lastFailedAt(null)
            .completedCount(0)
            .lastCompletedAt(null)
            .createdCount(0)
            .lastCreatedAt(null)
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.lastFailedAt()).isNull();
    assertThat(model.lastCompletedAt()).isNull();
    assertThat(model.lastCreatedAt()).isNull();
  }

  @TestTemplate
  public void shouldCleanupJobMetricsBatchesProperly() {
    // given
    final var recentA =
        JobMetricsBatchFixtures.createRandomized(
            b ->
                b.tenantId(TENANT1)
                    .jobType(JOB_TYPE_A)
                    .worker(WORKER_1)
                    .startTime(NOW)
                    .endTime(NOW)
                    .lastCreatedAt(NOW)
                    .lastCompletedAt(NOW)
                    .lastFailedAt(NOW)
                    .incompleteBatch(false));

    final var recentB =
        JobMetricsBatchFixtures.createRandomized(
            b ->
                b.tenantId(TENANT1)
                    .jobType(JOB_TYPE_B)
                    .worker(WORKER_1)
                    .startTime(NOW)
                    .endTime(NOW)
                    .lastCreatedAt(NOW)
                    .lastCompletedAt(NOW)
                    .lastFailedAt(NOW)
                    .incompleteBatch(false));

    final var oldTimestamp = NOW.minusYears(3);
    final var oldA =
        JobMetricsBatchFixtures.createRandomized(
            b ->
                b.tenantId(TENANT2)
                    .jobType(JOB_TYPE_A)
                    .worker(WORKER_1)
                    .startTime(oldTimestamp)
                    .endTime(oldTimestamp)
                    .lastCreatedAt(oldTimestamp)
                    .lastCompletedAt(oldTimestamp)
                    .lastFailedAt(oldTimestamp)
                    .incompleteBatch(false));

    final var oldB =
        JobMetricsBatchFixtures.createRandomized(
            b ->
                b.tenantId(TENANT2)
                    .jobType(JOB_TYPE_B)
                    .worker(WORKER_2)
                    .startTime(oldTimestamp)
                    .endTime(oldTimestamp)
                    .lastCreatedAt(oldTimestamp)
                    .lastCompletedAt(oldTimestamp)
                    .lastFailedAt(oldTimestamp)
                    .incompleteBatch(false));

    JobMetricsBatchFixtures.createAndSaveMetrics(
        rdbmsWriters, List.of(recentA, recentB, oldA, oldB));

    // when - cleanup records older than 2 years
    final int deletedCount =
        jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 100);

    // then
    assertThat(deletedCount).isEqualTo(2);
  }

  @TestTemplate
  public void shouldCleanupJobMetricsBatchesWithLimit() {
    // given - create 5 old records
    final var oldTimestamp = NOW.minusYears(3);
    final var metrics =
        Stream.iterate(0, i -> i + 1)
            .limit(5)
            .map(
                i ->
                    JobMetricsBatchFixtures.createRandomized(
                        b -> b.startTime(oldTimestamp).endTime(oldTimestamp)))
            .toList();

    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - cleanup with limit of 3
    final int deletedCount =
        jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 3);

    // then
    assertThat(deletedCount).isEqualTo(3);
  }

  @TestTemplate
  public void shouldNotCleanupRecentJobMetricsBatches() {
    // given - create only recent records
    final var startTime1 = NOW.minusMinutes(3);
    final var endTime1 = NOW.minusMinutes(2);
    final var metric1 =
        JobMetricsBatchFixtures.createRandomized(b -> b.startTime(startTime1).endTime(endTime1));

    final var startTime2 = NOW.minusMinutes(2);
    final var endTime2 = NOW.minusMinutes(1);
    final var metric2 =
        JobMetricsBatchFixtures.createRandomized(b -> b.startTime(startTime2).endTime(endTime2));

    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, List.of(metric1, metric2));

    // when - cleanup records older than 2 years (none should be affected)
    final int deletedCount =
        jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 100);

    // then
    assertThat(deletedCount).isEqualTo(0);
  }

  // ==========================================================================
  // Job Type Statistics Tests
  // ==========================================================================

  @TestTemplate
  public void shouldAggregateJobStatisticsByJobType() {
    // given
    final var metricJobTypeA = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_A));

    final var metricJobTypeB = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_B));

    JobMetricsBatchFixtures.createAndSaveMetrics(
        rdbmsWriters, List.of(metricJobTypeA, metricJobTypeB));

    final var createdMetrics = List.of(metricJobTypeA, metricJobTypeB);

    // when
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(createdMetrics).minusMinutes(1))
                                .to(getMaxEndTime(createdMetrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.total()).isEqualTo(2);
    assertThat(actual.items()).hasSize(2);

    // Verify SQL ORDER BY JOB_TYPE ASC - results should already be sorted
    assertThat(actual.items().get(0).jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(actual.items().get(1).jobType()).isEqualTo(JOB_TYPE_B);

    final var jobTypeA = normalizeToUtc(actual.items().get(0));
    assertThat(jobTypeA.jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(jobTypeA.created().count()).isEqualTo(metricJobTypeA.createdCount());
    assertThat(jobTypeA.created().lastUpdatedAt()).isEqualTo(metricJobTypeA.lastCreatedAt());
    assertThat(jobTypeA.completed().count()).isEqualTo(metricJobTypeA.completedCount());
    assertThat(jobTypeA.completed().lastUpdatedAt()).isEqualTo(metricJobTypeA.lastCompletedAt());
    assertThat(jobTypeA.failed().count()).isEqualTo(metricJobTypeA.failedCount());
    assertThat(jobTypeA.failed().lastUpdatedAt()).isEqualTo(metricJobTypeA.lastFailedAt());
    assertThat(jobTypeA.workers()).isEqualTo(1);

    final var jobTypeB = normalizeToUtc(actual.items().get(1));
    assertThat(jobTypeB.jobType()).isEqualTo(JOB_TYPE_B);
    assertThat(jobTypeB.created().count()).isEqualTo(metricJobTypeB.createdCount());
    assertThat(jobTypeB.created().lastUpdatedAt()).isEqualTo(metricJobTypeB.lastCreatedAt());
    assertThat(jobTypeB.completed().count()).isEqualTo(metricJobTypeB.completedCount());
    assertThat(jobTypeB.completed().lastUpdatedAt()).isEqualTo(metricJobTypeB.lastCompletedAt());
    assertThat(jobTypeB.failed().count()).isEqualTo(metricJobTypeB.failedCount());
    assertThat(jobTypeB.failed().lastUpdatedAt()).isEqualTo(metricJobTypeB.lastFailedAt());
    assertThat(jobTypeB.workers()).isEqualTo(1);
  }

  @TestTemplate
  public void shouldAggregateJobTypeStatsAcrossMultipleBatches() {
    // given - multiple batches for same job type
    final var metric1 = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_A));

    final var metric2 = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_A));

    final var metric3 = JobMetricsBatchFixtures.createRandomized(b -> b.jobType(JOB_TYPE_A));

    final List<JobMetricsBatchDbModel> metrics = List.of(metric1, metric2, metric3);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - aggregated across both batches
    assertThat(actual.total()).isEqualTo(1);
    assertThat(actual.items()).hasSize(1);

    final var stats = normalizeToUtc(actual.items().get(0));
    assertThat(stats.jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(stats.created().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::createdCount).sum());
    assertThat(stats.completed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::completedCount).sum());
    assertThat(stats.failed().count())
        .isEqualTo(metrics.stream().mapToLong(JobMetricsBatchDbModel::failedCount).sum());

    final var expectedLastCreatedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastCreatedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();
    final var expectedLastCompletedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastCompletedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();
    final var expectedLastFailedAt =
        metrics.stream()
            .map(JobMetricsBatchDbModel::lastFailedAt)
            .max(OffsetDateTime::compareTo)
            .orElseThrow();

    assertThat(stats.created().lastUpdatedAt()).isEqualTo(expectedLastCreatedAt);
    assertThat(stats.completed().lastUpdatedAt()).isEqualTo(expectedLastCompletedAt);
    assertThat(stats.failed().lastUpdatedAt()).isEqualTo(expectedLastFailedAt);
    final var workersNb = metrics.stream().map(JobMetricsBatchDbModel::worker).distinct().count();
    assertThat(stats.workers()).isEqualTo(workersNb);
  }

  @TestTemplate
  public void shouldCountDistinctWorkersPerJobType() {
    // given - multiple workers for same job type
    final var metricWorker1 =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A).worker(WORKER_1));

    final var metricWorker2 =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A).worker(WORKER_2));

    final var metrics = List.of(metricWorker1, metricWorker2);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(actual.items()).hasSize(1);
    assertThat(actual.items().get(0).jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(actual.items().get(0).workers()).isEqualTo(2); // WORKER_1 and WORKER_2
  }

  @TestTemplate
  public void shouldFilterJobTypeStatsByTimeRange() {
    // given
    final var metricOld =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.startTime(NOW.minusMinutes(30)).endTime(NOW.minusMinutes(20)));

    final var metricMiddle =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.startTime(NOW.minusMinutes(10)).endTime(NOW));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricOld, metricMiddle);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter to only get middle batch
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(metricMiddle.startTime().minusSeconds(1))
                                .to(metricMiddle.endTime().plusSeconds(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only JOB_TYPE_B
    assertThat(actual.items()).hasSize(1);
    assertThat(actual.items().get(0).jobType()).isEqualTo(metricMiddle.jobType());
  }

  @TestTemplate
  public void shouldFilterJobTypeStatsByJobTypeExactMatch() {
    // given
    final var metricJobTypeA =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A).worker(WORKER_1));

    final var metricJobTypeB =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_B).worker(WORKER_1));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricJobTypeA, metricJobTypeB);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter by JOB_TYPE_A only
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1))
                                .jobTypeOperations(List.of(Operation.eq(JOB_TYPE_A))))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only JOB_TYPE_A
    assertThat(actual.items()).hasSize(1);
    assertThat(actual.items().getFirst().jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(actual.items().getFirst().created().count())
        .isEqualTo(metricJobTypeA.createdCount());
    assertThat(actual.items().getFirst().completed().count())
        .isEqualTo(metricJobTypeA.completedCount());
    assertThat(actual.items().getFirst().failed().count()).isEqualTo(metricJobTypeA.failedCount());
    assertThat(toUtc(actual.items().getFirst().completed().lastUpdatedAt()))
        .isEqualTo(metricJobTypeA.lastCompletedAt());
    assertThat(toUtc(actual.items().getFirst().created().lastUpdatedAt()))
        .isEqualTo(metricJobTypeA.lastCreatedAt());
    assertThat(toUtc(actual.items().getFirst().failed().lastUpdatedAt()))
        .isEqualTo(metricJobTypeA.lastFailedAt());
  }

  @TestTemplate
  public void shouldFilterJobTypeStatsByJobTypeLike() {
    // given
    final var metricFetchOrders =
        JobMetricsBatchFixtures.createRandomized(b -> b.jobType("fetch-orders"));

    final var metricFetchCustomers =
        JobMetricsBatchFixtures.createRandomized(b -> b.jobType("fetch-customers"));

    final var metricProcessPayment =
        JobMetricsBatchFixtures.createRandomized(b -> b.jobType("process-payment"));

    final List<JobMetricsBatchDbModel> metrics =
        List.of(metricFetchOrders, metricFetchCustomers, metricProcessPayment);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter using like operation (use * wildcard, not %)
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1))
                                .jobTypeOperations(List.of(Operation.like("fetch-*"))))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only fetch-* job types, ordered by JOB_TYPE ASC from SQL
    assertThat(actual.items()).hasSize(2);
    final var jobTypes = actual.items().stream().map(JobTypeStatisticsEntity::jobType).toList();
    assertThat(jobTypes).containsExactly("fetch-customers", "fetch-orders");
  }

  @TestTemplate
  public void shouldFilterJobTypeStatsByJobTypeIn() {
    // given
    final var metricA =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_A).worker(WORKER_1));

    final var metricB =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType(JOB_TYPE_B).worker(WORKER_1));

    final var metricC =
        JobMetricsBatchFixtures.createRandomized(
            b -> b.tenantId(TENANT1).jobType("jobTypeC").worker(WORKER_1));

    final List<JobMetricsBatchDbModel> metrics = List.of(metricA, metricB, metricC);
    JobMetricsBatchFixtures.createAndSaveMetrics(rdbmsWriters, metrics);

    // when - filter using in operation
    final var actual =
        jobMetricsBatchReader.getJobTypeStatistics(
            JobTypeStatisticsQuery.of(
                q ->
                    q.filter(
                        f ->
                            f.from(getMinStartTime(metrics).minusMinutes(1))
                                .to(getMaxEndTime(metrics).plusMinutes(1))
                                .jobTypeOperations(List.of(Operation.in(JOB_TYPE_A, JOB_TYPE_B))))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only JOB_TYPE_A and JOB_TYPE_B, ordered by JOB_TYPE ASC from SQL
    assertThat(actual.items()).hasSize(2);
    final var jobTypes = actual.items().stream().map(JobTypeStatisticsEntity::jobType).toList();
    assertThat(jobTypes).containsExactly(JOB_TYPE_A, JOB_TYPE_B);
  }
}
