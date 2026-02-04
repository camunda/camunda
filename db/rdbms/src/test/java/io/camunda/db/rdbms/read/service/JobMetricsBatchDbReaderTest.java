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
import io.camunda.db.rdbms.sql.JobMetricsBatchMapper;
import io.camunda.search.query.GlobalJobStatisticsQuery;
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
}
