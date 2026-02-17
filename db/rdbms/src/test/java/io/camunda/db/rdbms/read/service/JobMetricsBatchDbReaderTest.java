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
import io.camunda.db.rdbms.read.domain.JobTypeStatisticsDbResult;
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.search.query.GlobalJobStatisticsQuery;
import io.camunda.search.query.JobTypeStatisticsQuery;
import io.camunda.security.reader.AuthorizationCheck;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.TenantCheck;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMetricsBatchDbReaderTest {
  private final JobMetricsBatchMapper jobMetricsBatchMapper = mock(JobMetricsBatchMapper.class);
  private final JobMetricsBatchDbReader jobMetricsBatchDbReader =
      new JobMetricsBatchDbReader(jobMetricsBatchMapper);

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
  void shouldReturnEmptyGlobalJobStatisticsWhenMapperReturnsNull() {
    // given
    when(jobMetricsBatchMapper.globalJobStatistics(any())).thenReturn(null);

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

    when(jobMetricsBatchMapper.countJobTypeStatistics(any())).thenReturn(2L);
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
    // given
    when(jobMetricsBatchMapper.countJobTypeStatistics(any())).thenReturn(0L);

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
  void shouldReturnEmptyJobTypeStatisticsWhenCountIsNull() {
    // given
    when(jobMetricsBatchMapper.countJobTypeStatistics(any())).thenReturn(null);

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

    when(jobMetricsBatchMapper.countJobTypeStatistics(any())).thenReturn(1L);
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

    final var item = result.items().get(0);
    assertThat(item.jobType()).isEqualTo("task-a");
    assertThat(item.created().count()).isZero();
    assertThat(item.created().lastUpdatedAt()).isNull();
    assertThat(item.completed().count()).isZero();
    assertThat(item.completed().lastUpdatedAt()).isNull();
    assertThat(item.failed().count()).isZero();
    assertThat(item.failed().lastUpdatedAt()).isNull();
    assertThat(item.workers()).isZero();
  }
}
