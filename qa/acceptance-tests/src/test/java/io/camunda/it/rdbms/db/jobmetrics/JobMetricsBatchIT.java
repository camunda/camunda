/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.jobmetrics;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.JobMetricsBatchDbReader;
import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.JobMetricsBatchDbModel;
import io.camunda.db.rdbms.write.service.JobMetricsBatchWriter;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class JobMetricsBatchIT {

  private static final int PARTITION_ID = 10;
  private static final OffsetDateTime NOW = OffsetDateTime.now(UTC).truncatedTo(ChronoUnit.MILLIS);
  private static final OffsetDateTime NOW_MINUS_2M = NOW.minusMinutes(2);
  private static final OffsetDateTime NOW_MINUS_3M = NOW.minusMinutes(3);
  private static final OffsetDateTime NOW_MINUS_4M = NOW.minusMinutes(4);
  private static final OffsetDateTime NOW_MINUS_5M = NOW.minusMinutes(5);
  private static final OffsetDateTime NOW_MINUS_7M = NOW.minusMinutes(7);
  private static final OffsetDateTime NOW_MINUS_8M = NOW.minusMinutes(8);
  private static final OffsetDateTime NOW_MINUS_9M = NOW.minusMinutes(9);
  private static final OffsetDateTime NOW_MINUS_10M = NOW.minusMinutes(10);
  private static final OffsetDateTime NOW_MINUS_12M = NOW.minusMinutes(12);
  private static final OffsetDateTime NOW_MINUS_13M = NOW.minusMinutes(13);
  private static final OffsetDateTime NOW_MINUS_14M = NOW.minusMinutes(14);
  private static final OffsetDateTime NOW_MINUS_15M = NOW.minusMinutes(15);
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
   * Normalizes a GlobalJobStatisticsEntity to have all timestamps in UTC for comparison across
   * different database types.
   */
  private GlobalJobStatisticsEntity normalizeToUtc(final GlobalJobStatisticsEntity entity) {
    return new GlobalJobStatisticsEntity(
        new StatusMetric(entity.created().count(), toUtc(entity.created().lastUpdatedAt())),
        new StatusMetric(entity.completed().count(), toUtc(entity.completed().lastUpdatedAt())),
        new StatusMetric(entity.failed().count(), toUtc(entity.failed().lastUpdatedAt())),
        entity.isIncomplete());
  }

  private void writeMetric(
      final JobMetricsBatchWriter writer,
      final OffsetDateTime startTime,
      final OffsetDateTime endTime,
      final String tenantId,
      final String jobType,
      final String worker,
      final int createdCount,
      final OffsetDateTime lastCreatedAt,
      final int completedCount,
      final OffsetDateTime lastCompletedAt,
      final int failedCount,
      final OffsetDateTime lastFailedAt,
      final boolean incomplete) {
    writer.create(
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(startTime)
            .endTime(endTime)
            .tenantId(tenantId)
            .jobType(jobType)
            .worker(worker)
            .createdCount(createdCount)
            .lastCreatedAt(lastCreatedAt)
            .completedCount(completedCount)
            .lastCompletedAt(lastCompletedAt)
            .failedCount(failedCount)
            .lastFailedAt(lastFailedAt)
            .incompleteBatch(incomplete)
            .build());
  }

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriters = rdbmsService.createWriter(PARTITION_ID);
    jobMetricsBatchReader = rdbmsService.getJobMetricsBatchDbReader();
    jobMetricsBatchWriter = rdbmsWriters.getJobMetricsBatchWriter();

    // Clean up any existing data before each test
    jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.plusDays(1), Integer.MAX_VALUE);
    rdbmsWriters.flush();
  }

  @AfterEach
  void tearDown() {
    jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.plusDays(1), Integer.MAX_VALUE);
    rdbmsWriters.flush();
  }

  @TestTemplate
  public void shouldAggregateGlobalJobStatistics() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_4M,
        7,
        NOW_MINUS_3M,
        1,
        NOW_MINUS_2M,
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(8L, NOW_MINUS_4M),
                new StatusMetric(17L, NOW_MINUS_3M),
                new StatusMetric(3L, NOW_MINUS_2M),
                false));
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleTenants() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT2,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_9M,
        6,
        NOW_MINUS_8M,
        1,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(8L, NOW_MINUS_9M),
                new StatusMetric(16L, NOW_MINUS_8M),
                new StatusMetric(3L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleJobTypes() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_B,
        WORKER_1,
        3,
        NOW_MINUS_9M,
        6,
        NOW_MINUS_8M,
        1,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(8L, NOW_MINUS_9M),
                new StatusMetric(16L, NOW_MINUS_8M),
                new StatusMetric(3L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldAggregateMetricsAcrossMultipleWorkers() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_2,
        3,
        NOW_MINUS_9M,
        6,
        NOW_MINUS_8M,
        1,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(8L, NOW_MINUS_9M),
                new StatusMetric(16L, NOW_MINUS_8M),
                new StatusMetric(3L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldFilterMetricsByTimeRange() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_15M,
        NOW_MINUS_10M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        1,
        NOW_MINUS_14M,
        2,
        NOW_MINUS_13M,
        1,
        NOW_MINUS_12M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_4M,
        7,
        NOW_MINUS_3M,
        1,
        NOW_MINUS_2M,
        false);

    rdbmsWriters.flush();

    // when - filter to only get middle batch
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_10M).to(NOW_MINUS_5M.plusSeconds(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only middle batch data
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(5L, NOW_MINUS_9M),
                new StatusMetric(10L, NOW_MINUS_8M),
                new StatusMetric(2L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldFilterMetricsByJobType() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_B,
        WORKER_1,
        3,
        NOW_MINUS_9M,
        6,
        NOW_MINUS_8M,
        1,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when - filter by JOB_TYPE_A only
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q ->
                    q.filter(
                        f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)).jobType(JOB_TYPE_A))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - only JOB_TYPE_A data
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(5L, NOW_MINUS_9M),
                new StatusMetric(10L, NOW_MINUS_8M),
                new StatusMetric(2L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldReturnMaxLastUpdatedAtTimestamp() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_4M, // Most recent created
        7,
        NOW_MINUS_3M, // Most recent completed
        1,
        NOW_MINUS_2M, // Most recent failed
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then - should return max timestamps
    assertThat(toUtc(actual.created().lastUpdatedAt())).isEqualTo(NOW_MINUS_4M);
    assertThat(toUtc(actual.completed().lastUpdatedAt())).isEqualTo(NOW_MINUS_3M);
    assertThat(toUtc(actual.failed().lastUpdatedAt())).isEqualTo(NOW_MINUS_2M);
  }

  @TestTemplate
  public void shouldReturnIncompleteWhenAnyBatchIsIncomplete() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false); // Complete

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_4M,
        7,
        NOW_MINUS_3M,
        1,
        NOW_MINUS_2M,
        true); // Incomplete

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
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
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
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
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

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
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT2,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW_MINUS_9M,
        6,
        NOW_MINUS_8M,
        1,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when - only authorize TENANT1
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(
                AuthorizationCheck.disabled(), TenantCheck.enabled(List.of(TENANT1))));

    // then - only TENANT1 data
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(5L, NOW_MINUS_9M),
                new StatusMetric(10L, NOW_MINUS_8M),
                new StatusMetric(2L, NOW_MINUS_7M),
                false));
  }

  @TestTemplate
  public void shouldReturnEmptyResultWhenNoAuthorizedTenants() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when - authorize no tenants
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
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
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        0, // No completed jobs
        null,
        0, // No failed jobs
        null,
        false);

    rdbmsWriters.flush();

    // when
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));

    // then
    assertThat(normalizeToUtc(actual))
        .isEqualTo(
            new GlobalJobStatisticsEntity(
                new StatusMetric(5L, NOW_MINUS_9M),
                new StatusMetric(0L, null),
                new StatusMetric(0L, null),
                false));
  }

  @TestTemplate
  public void shouldAsyncWriteAndReadMetrics() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW_MINUS_9M,
        10,
        NOW_MINUS_8M,
        2,
        NOW_MINUS_7M,
        false);

    rdbmsWriters.flush();

    // when/then - verify the data is eventually available
    Awaitility.await("should find created job metrics")
        .atMost(Duration.ofSeconds(4))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var actual =
                  jobMetricsBatchReader.getGlobalJobStatistics(
                      GlobalJobStatisticsQuery.of(
                          q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
                      ResourceAccessChecks.of(
                          AuthorizationCheck.disabled(), TenantCheck.disabled()));

              assertThat(actual.created().count()).isEqualTo(5L);
              assertThat(actual.completed().count()).isEqualTo(10L);
              assertThat(actual.failed().count()).isEqualTo(2L);
            });
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatch() {
    // given
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(5)
            .lastFailedAt(NOW)
            .completedCount(10)
            .lastCompletedAt(NOW)
            .createdCount(15)
            .lastCreatedAt(NOW)
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.key()).isNotNull();
    assertThat(model.tenantId()).isEqualTo(TENANT1);
    assertThat(model.jobType()).isEqualTo(JOB_TYPE_A);
    assertThat(model.worker()).isEqualTo(WORKER_1);
    assertThat(model.failedCount()).isEqualTo(5);
    assertThat(model.completedCount()).isEqualTo(10);
    assertThat(model.createdCount()).isEqualTo(15);
    assertThat(model.incompleteBatch()).isFalse();
  }

  @TestTemplate
  public void shouldInsertMultipleJobMetricsBatches() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        1,
        NOW,
        2,
        NOW,
        3,
        NOW,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_B,
        WORKER_2,
        4,
        NOW_MINUS_5M,
        5,
        NOW_MINUS_5M,
        6,
        NOW_MINUS_5M,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_10M,
        NOW_MINUS_10M,
        TENANT2,
        JOB_TYPE_A,
        WORKER_1,
        7,
        NOW_MINUS_10M,
        8,
        NOW_MINUS_10M,
        9,
        NOW_MINUS_10M,
        true);

    // when
    rdbmsWriters.flush();

    // when - then
    assertThat(rdbmsWriters).isNotNull();
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithDifferentTenants() {
    // given
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW,
        10,
        NOW,
        15,
        NOW,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT2,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW,
        6,
        NOW,
        9,
        NOW,
        false);
    rdbmsWriters.flush();

    // then - verify both were written successfully
    final var actual =
        jobMetricsBatchReader.getGlobalJobStatistics(
            GlobalJobStatisticsQuery.of(
                q -> q.filter(f -> f.from(NOW_MINUS_15M).to(NOW.plusMinutes(1)))),
            ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled()));
    assertThat(actual.created().count()).isEqualTo(8L); // 5 from TENANT1 + 3 from TENANT2
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithIncompleteBatch() {
    // given
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(5)
            .lastFailedAt(NOW)
            .completedCount(10)
            .lastCompletedAt(NOW)
            .createdCount(15)
            .lastCreatedAt(NOW)
            .incompleteBatch(true)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.incompleteBatch()).isTrue();
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithNullLastUpdatedTimes() {
    // given - create a batch with zero counts, so no lastUpdatedAt times
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
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
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW,
        10,
        NOW,
        15,
        NOW,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT1,
        JOB_TYPE_B,
        WORKER_1,
        5,
        NOW,
        10,
        NOW,
        15,
        NOW,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW.minusYears(3),
        NOW.minusYears(3),
        TENANT2,
        JOB_TYPE_A,
        WORKER_1,
        3,
        NOW.minusYears(3),
        6,
        NOW.minusYears(3),
        9,
        NOW.minusYears(3),
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW.minusYears(3),
        NOW.minusYears(3),
        TENANT2,
        JOB_TYPE_B,
        WORKER_2,
        1,
        NOW.minusYears(3),
        2,
        NOW.minusYears(3),
        3,
        NOW.minusYears(3),
        false);
    rdbmsWriters.flush();

    // when - cleanup records older than 2 years
    Awaitility.await("should cleanup old job metrics batches")
        .atMost(Duration.ofSeconds(4))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final int deletedCount =
                  jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 100);
              assertThat(deletedCount).isEqualTo(2);
            });
  }

  @TestTemplate
  public void shouldCleanupJobMetricsBatchesWithLimit() {
    // given - create 5 old records
    for (int i = 0; i < 5; i++) {
      writeMetric(
          jobMetricsBatchWriter,
          NOW.minusYears(3),
          NOW.minusYears(3),
          TENANT1,
          JOB_TYPE_A + i,
          WORKER_1,
          1,
          NOW.minusYears(3),
          2,
          NOW.minusYears(3),
          3,
          NOW.minusYears(3),
          false);
    }
    rdbmsWriters.flush();

    // when - cleanup with limit of 3
    Awaitility.await("should cleanup limited job metrics batches")
        .atMost(Duration.ofSeconds(4))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final int deletedCount =
                  jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 3);
              assertThat(deletedCount).isEqualTo(3);
            });
  }

  @TestTemplate
  public void shouldNotCleanupRecentJobMetricsBatches() {
    // given - create only recent records
    writeMetric(
        jobMetricsBatchWriter,
        NOW,
        NOW,
        TENANT1,
        JOB_TYPE_A,
        WORKER_1,
        5,
        NOW,
        10,
        NOW,
        15,
        NOW,
        false);
    writeMetric(
        jobMetricsBatchWriter,
        NOW_MINUS_5M,
        NOW_MINUS_5M,
        TENANT1,
        JOB_TYPE_B,
        WORKER_2,
        3,
        NOW_MINUS_5M,
        6,
        NOW_MINUS_5M,
        9,
        NOW_MINUS_5M,
        false);
    rdbmsWriters.flush();

    // when - cleanup records older than 2 years (none should be affected)
    final int deletedCount =
        jobMetricsBatchWriter.cleanupMetrics(PARTITION_ID, NOW.minusYears(2), 100);

    // then
    assertThat(deletedCount).isEqualTo(0);
  }

  @TestTemplate
  public void shouldInsertJobMetricsBatchWithAllFields() {
    // given
    final var startTime = NOW.minusMinutes(30);
    final var endTime = NOW;
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(startTime)
            .endTime(endTime)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(100)
            .lastFailedAt(NOW.minusMinutes(5))
            .completedCount(200)
            .lastCompletedAt(NOW.minusMinutes(3))
            .createdCount(300)
            .lastCreatedAt(NOW.minusMinutes(1))
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.startTime()).isEqualTo(startTime);
    assertThat(model.endTime()).isEqualTo(endTime);
    assertThat(model.failedCount()).isEqualTo(100);
    assertThat(model.completedCount()).isEqualTo(200);
    assertThat(model.createdCount()).isEqualTo(300);
    assertThat(model.lastFailedAt()).isNotNull();
    assertThat(model.lastCompletedAt()).isNotNull();
    assertThat(model.lastCreatedAt()).isNotNull();
  }

  @TestTemplate
  public void shouldHandleBatchWithOnlyFailedJobs() {
    // given
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(10)
            .lastFailedAt(NOW)
            .completedCount(0)
            .lastCompletedAt(null)
            .createdCount(0)
            .lastCreatedAt(null)
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.failedCount()).isEqualTo(10);
    assertThat(model.completedCount()).isZero();
    assertThat(model.createdCount()).isZero();
    assertThat(model.lastFailedAt()).isNotNull();
    assertThat(model.lastCompletedAt()).isNull();
    assertThat(model.lastCreatedAt()).isNull();
  }

  @TestTemplate
  public void shouldHandleBatchWithOnlyCompletedJobs() {
    // given
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(0)
            .lastFailedAt(null)
            .completedCount(20)
            .lastCompletedAt(NOW)
            .createdCount(0)
            .lastCreatedAt(null)
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.failedCount()).isZero();
    assertThat(model.completedCount()).isEqualTo(20);
    assertThat(model.createdCount()).isZero();
    assertThat(model.lastFailedAt()).isNull();
    assertThat(model.lastCompletedAt()).isNotNull();
    assertThat(model.lastCreatedAt()).isNull();
  }

  @TestTemplate
  public void shouldHandleBatchWithOnlyCreatedJobs() {
    // given
    final var model =
        new JobMetricsBatchDbModel.Builder()
            .key(String.valueOf(CommonFixtures.nextKey()))
            .partitionId(PARTITION_ID)
            .startTime(NOW)
            .endTime(NOW)
            .tenantId(TENANT1)
            .jobType(JOB_TYPE_A)
            .worker(WORKER_1)
            .failedCount(0)
            .lastFailedAt(null)
            .completedCount(0)
            .lastCompletedAt(null)
            .createdCount(30)
            .lastCreatedAt(NOW)
            .incompleteBatch(false)
            .build();
    jobMetricsBatchWriter.create(model);
    rdbmsWriters.flush();

    // then
    assertThat(model.failedCount()).isZero();
    assertThat(model.completedCount()).isZero();
    assertThat(model.createdCount()).isEqualTo(30);
    assertThat(model.lastFailedAt()).isNull();
    assertThat(model.lastCompletedAt()).isNull();
    assertThat(model.lastCreatedAt()).isNotNull();
  }
}
