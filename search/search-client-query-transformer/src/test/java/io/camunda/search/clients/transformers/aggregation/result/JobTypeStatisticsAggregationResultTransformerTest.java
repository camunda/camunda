/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_BY_TYPE;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static io.camunda.search.aggregation.JobTypeStatisticsAggregation.AGGREGATION_WORKERS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JobTypeStatisticsAggregationResultTransformerTest {

  private final JobTypeStatisticsAggregationResultTransformer transformer =
      new JobTypeStatisticsAggregationResultTransformer();

  @Test
  public void shouldReturnEmptyListWhenAggregationsEmpty() {
    // given
    final Map<String, AggregationResult> input = Map.of();

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  public void shouldTransformSingleJobTypeStatistics() {
    // given
    final String jobType = "task-handler";
    final long createdCount = 100L;
    final long completedCount = 80L;
    final long failedCount = 5L;
    final int workersCount = 3;
    final long createdTimestamp = 1706800000000L;
    final long completedTimestamp = 1706800100000L;
    final long failedTimestamp = 1706800200000L;
    final String endCursor = "cursorABC";

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(
                Map.of(
                    jobType,
                    jobTypeBucket(
                        createdCount,
                        createdTimestamp,
                        completedCount,
                        completedTimestamp,
                        failedCount,
                        failedTimestamp,
                        workersCount)),
                endCursor));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isEqualTo(endCursor);

    final var entity = result.items().get(0);
    assertThat(entity.jobType()).isEqualTo(jobType);

    assertThat(entity.created().count()).isEqualTo(createdCount);
    assertThat(entity.created().lastUpdatedAt())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(createdTimestamp), ZoneOffset.UTC));

    assertThat(entity.completed().count()).isEqualTo(completedCount);
    assertThat(entity.completed().lastUpdatedAt())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(completedTimestamp), ZoneOffset.UTC));

    assertThat(entity.failed().count()).isEqualTo(failedCount);
    assertThat(entity.failed().lastUpdatedAt())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(failedTimestamp), ZoneOffset.UTC));

    assertThat(entity.workers()).isEqualTo(workersCount);
  }

  @Test
  public void shouldTransformMultipleJobTypeStatistics() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(
                Map.of(
                    "type1", jobTypeBucket(10, 1000L, 8, 2000L, 1, 3000L, 2),
                    "type2", jobTypeBucket(20, 4000L, 15, 5000L, 2, 6000L, 3),
                    "type3", jobTypeBucket(5, 7000L, 5, 8000L, 0, 0L, 1)),
                "endCursorXYZ"));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting("jobType")
        .containsExactlyInAnyOrder("type1", "type2", "type3");
    assertThat(result.endCursor()).isEqualTo("endCursorXYZ");
  }

  @Test
  public void shouldHandleZeroCountsAndNullTimestamps() {
    // given
    final String jobType = "zero-count-type";
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(Map.of(jobType, jobTypeBucket(0, 0L, 0, 0L, 0, 0L, 0)), null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isNull();

    final var entity = result.items().get(0);
    assertThat(entity.jobType()).isEqualTo(jobType);
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
    assertThat(entity.workers()).isZero();
  }

  @Test
  public void shouldHandleMissingSubAggregations() {
    // given - bucket exists but has no sub-aggregations
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(Map.of("missing-subs", new AggregationResult(10L, Map.of())), null));

    // when
    final var result = transformer.apply(input);

    // then - entity with null aggregations should be filtered out
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  public void shouldHandlePartialSubAggregations() {
    // given - only created aggregation is present
    final String jobType = "partial-type";
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_CREATED,
            statusBucket(25L, 1706800000000L),
            AGGREGATION_WORKERS,
            new AggregationResult(2L, Map.of()));

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(Map.of(jobType, new AggregationResult(0L, subAggs)), "cursor"));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isEqualTo("cursor");

    final var entity = result.items().get(0);
    assertThat(entity.jobType()).isEqualTo(jobType);
    assertThat(entity.created().count()).isEqualTo(25L);
    assertThat(entity.created().lastUpdatedAt()).isNotNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
    assertThat(entity.workers()).isEqualTo(2);
  }

  @Test
  public void shouldHandleNullDocCountInSubAggregations() {
    // given
    final String jobType = "null-counts";
    final Map<String, AggregationResult> statusSubAggs =
        Map.of(
            AGGREGATION_COUNT, new AggregationResult(null, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(null, Map.of()));

    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_CREATED, new AggregationResult(10L, statusSubAggs),
            AGGREGATION_COMPLETED, new AggregationResult(10L, statusSubAggs),
            AGGREGATION_FAILED, new AggregationResult(10L, statusSubAggs),
            AGGREGATION_WORKERS, new AggregationResult(null, Map.of()));

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TYPE,
            byTypeBucket(Map.of(jobType, new AggregationResult(0L, subAggs)), null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);

    final var entity = result.items().get(0);
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
    assertThat(entity.workers()).isZero();
  }

  @Test
  public void shouldHandleNullByTypeAggregation() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_TYPE, new AggregationResult(0L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  private static AggregationResult byTypeBucket(
      final Map<String, AggregationResult> jobTypeBuckets) {
    return byTypeBucket(jobTypeBuckets, null);
  }

  private static AggregationResult byTypeBucket(
      final Map<String, AggregationResult> jobTypeBuckets, final String endCursor) {
    return new AggregationResult(0L, jobTypeBuckets, List.of(), endCursor);
  }

  private static AggregationResult jobTypeBucket(
      final long createdCount,
      final long createdTimestamp,
      final long completedCount,
      final long completedTimestamp,
      final long failedCount,
      final long failedTimestamp,
      final int workersCount) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_CREATED, statusBucket(createdCount, createdTimestamp),
            AGGREGATION_COMPLETED, statusBucket(completedCount, completedTimestamp),
            AGGREGATION_FAILED, statusBucket(failedCount, failedTimestamp),
            AGGREGATION_WORKERS, new AggregationResult((long) workersCount, Map.of()));

    return new AggregationResult(0L, subAggs);
  }

  private static AggregationResult statusBucket(final long count, final long lastUpdatedAtMillis) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_COUNT, new AggregationResult(count, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(lastUpdatedAtMillis, Map.of()));

    return new AggregationResult(0L, subAggs);
  }
}
