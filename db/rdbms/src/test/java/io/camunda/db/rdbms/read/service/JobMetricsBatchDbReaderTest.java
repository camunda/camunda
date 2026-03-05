/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.read.domain.GlobalJobStatisticsDbResult;
import io.camunda.db.rdbms.read.domain.JobErrorStatisticsDbResult;
import io.camunda.db.rdbms.read.domain.JobTimeSeriesStatisticsDbResult;
import io.camunda.db.rdbms.read.domain.JobTypeStatisticsDbResult;
import io.camunda.db.rdbms.read.domain.JobWorkerStatisticsDbResult;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobErrorStatisticsQuery;
import io.camunda.search.query.JobTimeSeriesStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.search.query.JobWorkerStatisticsQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMetricsBatchDbReaderTest {
  private final JobMetricsBatchMapper jobMetricsBatchMapper = mock(JobMetricsBatchMapper.class);
  private final JobMetricsBatchDbReader jobMetricsBatchDbReader =
      new JobMetricsBatchDbReader(jobMetricsBatchMapper, AbstractEntityReaderTest.TEST_CONFIG);

  @Test
  void shouldReturnEmptyGlobalJobStatisticsWhenTenantCheckEnabledButNoTenants() {
    // given
    final var query =
        GlobalJobStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = jobMetricsBatchDbReader.getGlobalJobStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.created().count()).isZero();
    assertThat(result.created().lastUpdatedAt()).isNull();
    assertThat(result.completed().count()).isZero();
    assertThat(result.completed().lastUpdatedAt()).isNull();
    assertThat(result.failed().count()).isZero();
    assertThat(result.failed().lastUpdatedAt()).isNull();
    assertThat(result.isIncomplete()).isFalse();
    verifyNoInteractions(jobMetricsBatchMapper);
  }

  @Test
  void shouldReturnGlobalJobStatisticsFromMapper() {
    // given
    final var lastCreatedAt = OffsetDateTime.now().minusHours(1);
    final var lastCompletedAt = OffsetDateTime.now().minusHours(2);
    final var lastFailedAt = OffsetDateTime.now().minusHours(3);
    final var dbResult =
        new GlobalJobStatisticsDbResult(
            100L, lastCreatedAt, 80L, lastCompletedAt, 5L, lastFailedAt, false);
    when(jobMetricsBatchMapper.globalJobStatistics(any())).thenReturn(dbResult);

    final var query =
        GlobalJobStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getGlobalJobStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.created().count()).isEqualTo(100L);
    assertThat(result.created().lastUpdatedAt()).isEqualTo(lastCreatedAt);
    assertThat(result.completed().count()).isEqualTo(80L);
    assertThat(result.completed().lastUpdatedAt()).isEqualTo(lastCompletedAt);
    assertThat(result.failed().count()).isEqualTo(5L);
    assertThat(result.failed().lastUpdatedAt()).isEqualTo(lastFailedAt);
    assertThat(result.isIncomplete()).isFalse();
  }

  @Test
  void shouldReturnGlobalJobStatisticsWithIncompleteFlag() {
    // given
    final var dbResult = new GlobalJobStatisticsDbResult(10L, null, 5L, null, 1L, null, true);
    when(jobMetricsBatchMapper.globalJobStatistics(any())).thenReturn(dbResult);

    final var query =
        GlobalJobStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getGlobalJobStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.isIncomplete()).isTrue();
  }

  @Test
  void shouldHandleNullCountsInDbResult() {
    // given
    final var dbResult = new GlobalJobStatisticsDbResult(null, null, null, null, null, null, null);
    when(jobMetricsBatchMapper.globalJobStatistics(any())).thenReturn(dbResult);

    final var query =
        GlobalJobStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getGlobalJobStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.created().count()).isZero();
    assertThat(result.completed().count()).isZero();
    assertThat(result.failed().count()).isZero();
    assertThat(result.isIncomplete()).isFalse();
  }

  @Test
  void shouldReturnEmptyJobTypeStatisticsWhenTenantCheckEnabledButNoTenants() {
    // given
    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
    verifyNoInteractions(jobMetricsBatchMapper);
  }

  @Test
  void shouldReturnJobTypeStatisticsFromMapper() {
    // given
    final var lastCreatedAt1 = OffsetDateTime.now().minusHours(1);
    final var lastCompletedAt1 = OffsetDateTime.now().minusHours(2);
    final var lastFailedAt1 = OffsetDateTime.now().minusHours(3);
    final var lastCreatedAt2 = OffsetDateTime.now().minusHours(4);
    final var lastCompletedAt2 = OffsetDateTime.now().minusHours(5);
    final var lastFailedAt2 = OffsetDateTime.now().minusHours(6);

    final var dbResult1 =
        new JobTypeStatisticsDbResult(
            "task-a", 100L, lastCreatedAt1, 80L, lastCompletedAt1, 5L, lastFailedAt1, 3);
    final var dbResult2 =
        new JobTypeStatisticsDbResult(
            "task-b", 50L, lastCreatedAt2, 40L, lastCompletedAt2, 2L, lastFailedAt2, 2);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult1, dbResult2));

    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);

    final var item1 = result.items().get(0);
    assertThat(item1.jobType()).isEqualTo("task-a");
    assertThat(item1.created().count()).isEqualTo(100L);
    assertThat(item1.created().lastUpdatedAt()).isEqualTo(lastCreatedAt1);
    assertThat(item1.completed().count()).isEqualTo(80L);
    assertThat(item1.completed().lastUpdatedAt()).isEqualTo(lastCompletedAt1);
    assertThat(item1.failed().count()).isEqualTo(5L);
    assertThat(item1.failed().lastUpdatedAt()).isEqualTo(lastFailedAt1);
    assertThat(item1.workers()).isEqualTo(3);

    final var item2 = result.items().get(1);
    assertThat(item2.jobType()).isEqualTo("task-b");
    assertThat(item2.created().count()).isEqualTo(50L);
    assertThat(item2.created().lastUpdatedAt()).isEqualTo(lastCreatedAt2);
    assertThat(item2.completed().count()).isEqualTo(40L);
    assertThat(item2.completed().lastUpdatedAt()).isEqualTo(lastCompletedAt2);
    assertThat(item2.failed().count()).isEqualTo(2L);
    assertThat(item2.failed().lastUpdatedAt()).isEqualTo(lastFailedAt2);
    assertThat(item2.workers()).isEqualTo(2);
  }

  @Test
  void shouldReturnEmptyJobTypeStatisticsWhenCountIsZero() {
    // given no results from the mapper, which is different from an empty list (which would indicate
    // that there are job types but all counts are zero)

    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldHandleNullCountsInJobTypeStatisticsDbResult() {
    // given
    final var dbResult =
        new JobTypeStatisticsDbResult("task-a", null, null, null, null, null, null, null);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(1L);
    assertThat(result.items()).hasSize(1);

    final var item = result.items().getFirst();
    assertThat(item.jobType()).isEqualTo("task-a");
    assertThat(item.created().count()).isZero();
    assertThat(item.created().lastUpdatedAt()).isNull();
    assertThat(item.completed().count()).isZero();
    assertThat(item.completed().lastUpdatedAt()).isNull();
    assertThat(item.failed().count()).isZero();
    assertThat(item.failed().lastUpdatedAt()).isNull();
    assertThat(item.workers()).isZero();
  }

  @Test
  void shouldReturnJobTypeStatisticsWithMultipleJobTypes() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult1 = new JobTypeStatisticsDbResult("type-a", 100L, now, 90L, now, 5L, now, 3);
    final var dbResult2 = new JobTypeStatisticsDbResult("type-b", 50L, now, 45L, now, 2L, now, 2);
    final var dbResult3 = new JobTypeStatisticsDbResult("type-c", 25L, now, 20L, now, 1L, now, 1);

    when(jobMetricsBatchMapper.jobTypeStatistics(any()))
        .thenReturn(List.of(dbResult1, dbResult2, dbResult3));

    final var query =
        JobTypeStatisticsQuery.of(b -> b.filter(f -> f.from(now.minusDays(1)).to(now)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(3L);
    assertThat(result.items()).hasSize(3);
    assertThat(result.items()).extracting("jobType").containsExactly("type-a", "type-b", "type-c");
  }

  @Test
  void shouldReturnJobTypeStatisticsWithZeroWorkersCount() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult = new JobTypeStatisticsDbResult("task-type", 10L, now, 8L, now, 0L, now, 0);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobTypeStatisticsQuery.of(b -> b.filter(f -> f.from(now.minusDays(1)).to(now)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().workers()).isZero();
    assertThat(result.items().getFirst().failed().count()).isZero();
  }

  @Test
  void shouldReturnJobTypeStatisticsWithHighVolumeMetrics() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult =
        new JobTypeStatisticsDbResult(
            "high-volume-task", 1_000_000L, now, 950_000L, now, 50_000L, now, 100);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobTypeStatisticsQuery.of(b -> b.filter(f -> f.from(now.minusDays(7)).to(now)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().jobType()).isEqualTo("high-volume-task");
    assertThat(result.items().getFirst().created().count()).isEqualTo(1_000_000L);
    assertThat(result.items().getFirst().completed().count()).isEqualTo(950_000L);
    assertThat(result.items().getFirst().failed().count()).isEqualTo(50_000L);
    assertThat(result.items().getFirst().workers()).isEqualTo(100);
  }

  @Test
  void shouldReturnJobTypeStatisticsWithMixedTimestamps() {
    // given
    final var oldTimestamp = OffsetDateTime.now().minusDays(30);
    final var recentTimestamp = OffsetDateTime.now().minusHours(1);

    final var dbResult =
        new JobTypeStatisticsDbResult(
            "mixed-task", 100L, oldTimestamp, 80L, recentTimestamp, 5L, oldTimestamp, 5);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(oldTimestamp).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    final var item = result.items().getFirst();
    assertThat(item.created().lastUpdatedAt()).isEqualTo(oldTimestamp);
    assertThat(item.completed().lastUpdatedAt()).isEqualTo(recentTimestamp);
    assertThat(item.failed().lastUpdatedAt()).isEqualTo(oldTimestamp);
  }

  @Test
  void shouldReturnEmptyJobTypeStatisticsListWhenMapperReturnsEmptyList() {
    // given
    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of());

    final var query =
        JobTypeStatisticsQuery.of(
            b -> b.filter(f -> f.from(OffsetDateTime.now()).to(OffsetDateTime.now())));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnJobTypeStatisticsForLongJobTypeNames() {
    // given
    final var now = OffsetDateTime.now();
    final var longJobTypeName =
        "io.camunda.example.very.long.package.name.with.multiple.segments.MyVeryLongJobTypeName";
    final var dbResult =
        new JobTypeStatisticsDbResult(longJobTypeName, 10L, now, 9L, now, 1L, now, 2);

    when(jobMetricsBatchMapper.jobTypeStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobTypeStatisticsQuery.of(b -> b.filter(f -> f.from(now.minusDays(1)).to(now)));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobTypeStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().jobType()).isEqualTo(longJobTypeName);
  }

  // ===========================================================================
  // Job Worker Statistics Tests
  // ===========================================================================

  @Test
  void shouldReturnEmptyJobWorkerStatisticsWhenTenantCheckEnabledButNoTenants() {
    // given
    final var query =
        JobWorkerStatisticsQuery.of(
            b ->
                b.filter(
                    f ->
                        f.from(OffsetDateTime.now())
                            .to(OffsetDateTime.now())
                            .jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
    verifyNoInteractions(jobMetricsBatchMapper);
  }

  @Test
  void shouldReturnJobWorkerStatisticsFromMapper() {
    // given
    final var lastCreatedAt1 = OffsetDateTime.now().minusHours(1);
    final var lastCompletedAt1 = OffsetDateTime.now().minusHours(2);
    final var lastFailedAt1 = OffsetDateTime.now().minusHours(3);
    final var lastCreatedAt2 = OffsetDateTime.now().minusHours(4);
    final var lastCompletedAt2 = OffsetDateTime.now().minusHours(5);
    final var lastFailedAt2 = OffsetDateTime.now().minusHours(6);

    final var dbResult1 =
        new JobWorkerStatisticsDbResult(
            "worker-1", 100L, lastCreatedAt1, 80L, lastCompletedAt1, 5L, lastFailedAt1);
    final var dbResult2 =
        new JobWorkerStatisticsDbResult(
            "worker-2", 50L, lastCreatedAt2, 40L, lastCompletedAt2, 2L, lastFailedAt2);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any()))
        .thenReturn(List.of(dbResult1, dbResult2));

    final var query =
        JobWorkerStatisticsQuery.of(
            b ->
                b.filter(
                    f ->
                        f.from(OffsetDateTime.now())
                            .to(OffsetDateTime.now())
                            .jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);

    final var item1 = result.items().get(0);
    assertThat(item1.worker()).isEqualTo("worker-1");
    assertThat(item1.created().count()).isEqualTo(100L);
    assertThat(item1.created().lastUpdatedAt()).isEqualTo(lastCreatedAt1);
    assertThat(item1.completed().count()).isEqualTo(80L);
    assertThat(item1.completed().lastUpdatedAt()).isEqualTo(lastCompletedAt1);
    assertThat(item1.failed().count()).isEqualTo(5L);
    assertThat(item1.failed().lastUpdatedAt()).isEqualTo(lastFailedAt1);

    final var item2 = result.items().get(1);
    assertThat(item2.worker()).isEqualTo("worker-2");
    assertThat(item2.created().count()).isEqualTo(50L);
    assertThat(item2.created().lastUpdatedAt()).isEqualTo(lastCreatedAt2);
    assertThat(item2.completed().count()).isEqualTo(40L);
    assertThat(item2.completed().lastUpdatedAt()).isEqualTo(lastCompletedAt2);
    assertThat(item2.failed().count()).isEqualTo(2L);
    assertThat(item2.failed().lastUpdatedAt()).isEqualTo(lastFailedAt2);
  }

  @Test
  void shouldReturnEmptyJobWorkerStatisticsWhenCountIsZero() {
    // given - mapper returns null (no data for this query)
    final var query =
        JobWorkerStatisticsQuery.of(
            b ->
                b.filter(
                    f ->
                        f.from(OffsetDateTime.now())
                            .to(OffsetDateTime.now())
                            .jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldHandleNullCountsInJobWorkerStatisticsDbResult() {
    // given
    final var dbResult =
        new JobWorkerStatisticsDbResult("worker-1", null, null, null, null, null, null);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobWorkerStatisticsQuery.of(
            b ->
                b.filter(
                    f ->
                        f.from(OffsetDateTime.now())
                            .to(OffsetDateTime.now())
                            .jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(1L);
    assertThat(result.items()).hasSize(1);

    final var item = result.items().getFirst();
    assertThat(item.worker()).isEqualTo("worker-1");
    assertThat(item.created().count()).isZero();
    assertThat(item.created().lastUpdatedAt()).isNull();
    assertThat(item.completed().count()).isZero();
    assertThat(item.completed().lastUpdatedAt()).isNull();
    assertThat(item.failed().count()).isZero();
    assertThat(item.failed().lastUpdatedAt()).isNull();
  }

  @Test
  void shouldReturnJobWorkerStatisticsWithMultipleWorkers() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult1 = new JobWorkerStatisticsDbResult("worker-a", 100L, now, 90L, now, 5L, now);
    final var dbResult2 = new JobWorkerStatisticsDbResult("worker-b", 50L, now, 45L, now, 2L, now);
    final var dbResult3 = new JobWorkerStatisticsDbResult("worker-c", 25L, now, 20L, now, 1L, now);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any()))
        .thenReturn(List.of(dbResult1, dbResult2, dbResult3));

    final var query =
        JobWorkerStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(3L);
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting("worker")
        .containsExactly("worker-a", "worker-b", "worker-c");
  }

  @Test
  void shouldReturnJobWorkerStatisticsWithZeroFailedCount() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult =
        new JobWorkerStatisticsDbResult("reliable-worker", 10L, now, 10L, now, 0L, now);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobWorkerStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().worker()).isEqualTo("reliable-worker");
    assertThat(result.items().getFirst().failed().count()).isZero();
    assertThat(result.items().getFirst().completed().count()).isEqualTo(10L);
  }

  @Test
  void shouldReturnJobWorkerStatisticsWithHighVolumeMetrics() {
    // given
    final var now = OffsetDateTime.now();
    final var dbResult =
        new JobWorkerStatisticsDbResult(
            "high-volume-worker", 1_000_000L, now, 950_000L, now, 50_000L, now);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobWorkerStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(7)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().worker()).isEqualTo("high-volume-worker");
    assertThat(result.items().getFirst().created().count()).isEqualTo(1_000_000L);
    assertThat(result.items().getFirst().completed().count()).isEqualTo(950_000L);
    assertThat(result.items().getFirst().failed().count()).isEqualTo(50_000L);
  }

  @Test
  void shouldReturnJobWorkerStatisticsWithMixedTimestamps() {
    // given
    final var oldTimestamp = OffsetDateTime.now().minusDays(30);
    final var recentTimestamp = OffsetDateTime.now().minusHours(1);

    final var dbResult =
        new JobWorkerStatisticsDbResult(
            "worker-1", 100L, oldTimestamp, 80L, recentTimestamp, 5L, oldTimestamp);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobWorkerStatisticsQuery.of(
            b -> b.filter(f -> f.from(oldTimestamp).to(OffsetDateTime.now()).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    final var item = result.items().getFirst();
    assertThat(item.created().lastUpdatedAt()).isEqualTo(oldTimestamp);
    assertThat(item.completed().lastUpdatedAt()).isEqualTo(recentTimestamp);
    assertThat(item.failed().lastUpdatedAt()).isEqualTo(oldTimestamp);
  }

  @Test
  void shouldReturnEmptyJobWorkerStatisticsListWhenMapperReturnsEmptyList() {
    // given
    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of());

    final var query =
        JobWorkerStatisticsQuery.of(
            b ->
                b.filter(
                    f ->
                        f.from(OffsetDateTime.now())
                            .to(OffsetDateTime.now())
                            .jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnJobForLongWorkerNames() {
    // given
    final var now = OffsetDateTime.now();
    final var longWorkerName =
        "io.camunda.example.very.long.package.name.with.multiple.segments.MyVeryLongWorkerName";
    final var dbResult =
        new JobWorkerStatisticsDbResult(longWorkerName, 10L, now, 9L, now, 1L, now);

    when(jobMetricsBatchMapper.jobWorkerStatistics(any())).thenReturn(List.of(dbResult));

    final var query =
        JobWorkerStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobWorkerStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items().getFirst().worker()).isEqualTo(longWorkerName);
  }

  // -----------------------------------------------------------------------
  // getJobTimeSeriesStatistics
  // -----------------------------------------------------------------------

  @Test
  void shouldReturnEmptyTimeSeriesWhenTenantCheckEnabledButNoTenants() {
    // given
    final var now = OffsetDateTime.now();
    final var query =
        JobTimeSeriesStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result =
        jobMetricsBatchDbReader.getJobTimeSeriesStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
    verifyNoInteractions(jobMetricsBatchMapper);
  }

  @Test
  void shouldReturnTimeSeriesFromMapper() {
    // given
    final var bucket1 = OffsetDateTime.parse("2024-07-28T00:00:00Z");
    final var bucket2 = OffsetDateTime.parse("2024-07-28T01:00:00Z");
    final var lastUpdatedAt = OffsetDateTime.now();

    final var dbResult1 =
        new JobTimeSeriesStatisticsDbResult(
            bucket1, 10L, lastUpdatedAt, 8L, lastUpdatedAt, 2L, lastUpdatedAt);
    final var dbResult2 =
        new JobTimeSeriesStatisticsDbResult(
            bucket2, 5L, lastUpdatedAt, 4L, lastUpdatedAt, 1L, lastUpdatedAt);

    when(jobMetricsBatchMapper.jobTimeSeriesStatistics(any()))
        .thenReturn(List.of(dbResult1, dbResult2));

    final var now = OffsetDateTime.now();
    final var query =
        JobTimeSeriesStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result =
        jobMetricsBatchDbReader.getJobTimeSeriesStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);

    final var item1 = result.items().get(0);
    assertThat(item1.time()).isEqualTo(bucket1);
    assertThat(item1.created().count()).isEqualTo(10L);
    assertThat(item1.completed().count()).isEqualTo(8L);
    assertThat(item1.failed().count()).isEqualTo(2L);

    final var item2 = result.items().get(1);
    assertThat(item2.time()).isEqualTo(bucket2);
    assertThat(item2.created().count()).isEqualTo(5L);
    assertThat(item2.completed().count()).isEqualTo(4L);
    assertThat(item2.failed().count()).isEqualTo(1L);
  }

  @Test
  void shouldReturnTimeSeriesWithNullCountsDefaultingToZero() {
    // given
    final var bucket = OffsetDateTime.parse("2024-07-28T00:00:00Z");
    final var dbResult =
        new JobTimeSeriesStatisticsDbResult(bucket, null, null, null, null, null, null);
    when(jobMetricsBatchMapper.jobTimeSeriesStatistics(any())).thenReturn(List.of(dbResult));

    final var now = OffsetDateTime.now();
    final var query =
        JobTimeSeriesStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result =
        jobMetricsBatchDbReader.getJobTimeSeriesStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items()).hasSize(1);
    final var item = result.items().getFirst();
    assertThat(item.time()).isEqualTo(bucket);
    assertThat(item.created().count()).isZero();
    assertThat(item.created().lastUpdatedAt()).isNull();
    assertThat(item.completed().count()).isZero();
    assertThat(item.failed().count()).isZero();
  }

  @Test
  void shouldReturnEmptyTimeSeriesWhenMapperReturnsEmptyList() {
    // given
    when(jobMetricsBatchMapper.jobTimeSeriesStatistics(any())).thenReturn(List.of());

    final var now = OffsetDateTime.now();
    final var query =
        JobTimeSeriesStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result =
        jobMetricsBatchDbReader.getJobTimeSeriesStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnTimeSeriesWithTimestampsPreserved() {
    // given
    final var bucket = OffsetDateTime.parse("2024-07-28T12:00:00Z");
    final var lastCreated = OffsetDateTime.parse("2024-07-28T12:30:00Z");
    final var lastCompleted = OffsetDateTime.parse("2024-07-28T12:45:00Z");
    final var lastFailed = OffsetDateTime.parse("2024-07-28T12:59:00Z");

    final var dbResult =
        new JobTimeSeriesStatisticsDbResult(
            bucket, 100L, lastCreated, 90L, lastCompleted, 3L, lastFailed);
    when(jobMetricsBatchMapper.jobTimeSeriesStatistics(any())).thenReturn(List.of(dbResult));

    final var now = OffsetDateTime.now();
    final var query =
        JobTimeSeriesStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result =
        jobMetricsBatchDbReader.getJobTimeSeriesStatistics(query, resourceAccessChecks);

    // then
    final var item = result.items().getFirst();
    assertThat(item.created().lastUpdatedAt()).isEqualTo(lastCreated);
    assertThat(item.completed().lastUpdatedAt()).isEqualTo(lastCompleted);
    assertThat(item.failed().lastUpdatedAt()).isEqualTo(lastFailed);
  }

  // -----------------------------------------------------------------------
  // getJobErrorStatistics
  // -----------------------------------------------------------------------

  @Test
  void shouldReturnEmptyErrorStatisticsWhenTenantCheckEnabledButNoTenants() {
    // given
    final var now = OffsetDateTime.now();
    final var query =
        JobErrorStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.enabled(List.of()));

    // when
    final var result = jobMetricsBatchDbReader.getJobErrorStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
    verifyNoInteractions(jobMetricsBatchMapper);
  }

  @Test
  void shouldReturnErrorStatisticsFromMapper() {
    // given
    final var dbResult1 = new JobErrorStatisticsDbResult("IO_ERROR", "Disk full", 3);
    final var dbResult2 = new JobErrorStatisticsDbResult("TIMEOUT", "Connection timed out", 7);
    when(jobMetricsBatchMapper.jobErrorStatistics(any())).thenReturn(List.of(dbResult1, dbResult2));

    final var now = OffsetDateTime.now();
    final var query =
        JobErrorStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobErrorStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isEqualTo(2L);
    assertThat(result.items()).hasSize(2);

    assertThat(result.items().get(0).errorCode()).isEqualTo("IO_ERROR");
    assertThat(result.items().get(0).errorMessage()).isEqualTo("Disk full");
    assertThat(result.items().get(0).workers()).isEqualTo(3);

    assertThat(result.items().get(1).errorCode()).isEqualTo("TIMEOUT");
    assertThat(result.items().get(1).errorMessage()).isEqualTo("Connection timed out");
    assertThat(result.items().get(1).workers()).isEqualTo(7);
  }

  @Test
  void shouldReturnErrorStatisticsWithNullWorkersDefaultingToZero() {
    // given
    final var dbResult = new JobErrorStatisticsDbResult("IO_ERROR", "msg", null);
    when(jobMetricsBatchMapper.jobErrorStatistics(any())).thenReturn(List.of(dbResult));

    final var now = OffsetDateTime.now();
    final var query =
        JobErrorStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobErrorStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().workers()).isZero();
  }

  @Test
  void shouldReturnEmptyErrorStatisticsWhenMapperReturnsEmptyList() {
    // given
    when(jobMetricsBatchMapper.jobErrorStatistics(any())).thenReturn(List.of());

    final var now = OffsetDateTime.now();
    final var query =
        JobErrorStatisticsQuery.of(
            b -> b.filter(f -> f.from(now.minusDays(1)).to(now).jobType("some-task")));
    final ResourceAccessChecks resourceAccessChecks =
        ResourceAccessChecks.of(AuthorizationCheck.disabled(), TenantCheck.disabled());

    // when
    final var result = jobMetricsBatchDbReader.getJobErrorStatistics(query, resourceAccessChecks);

    // then
    assertThat(result.total()).isZero();
    assertThat(result.items()).isEmpty();
  }
}
