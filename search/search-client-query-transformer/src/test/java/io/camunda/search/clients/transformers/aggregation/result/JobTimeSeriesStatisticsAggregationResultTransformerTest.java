/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_BY_TIME;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobTimeSeriesStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JobTimeSeriesStatisticsAggregationResultTransformerTest {

  private final JobTimeSeriesStatisticsAggregationResultTransformer transformer =
      new JobTimeSeriesStatisticsAggregationResultTransformer();

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
  public void shouldTransformSingleTimeBucket() {
    // given
    final long bucketEpochMillis = 1706800000000L;
    final long createdCount = 10L;
    final long completedCount = 8L;
    final long failedCount = 1L;
    final long createdTimestamp = 1706800100000L;
    final long completedTimestamp = 1706800200000L;
    final long failedTimestamp = 1706800300000L;
    final String endCursor = "cursor123";

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TIME,
            byTimeBucket(
                Map.of(
                    String.valueOf(bucketEpochMillis),
                    timeBucket(
                        createdCount,
                        createdTimestamp,
                        completedCount,
                        completedTimestamp,
                        failedCount,
                        failedTimestamp)),
                endCursor));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isEqualTo(endCursor);

    final var entity = result.items().get(0);
    assertThat(entity.time())
        .isEqualTo(
            OffsetDateTime.ofInstant(Instant.ofEpochMilli(bucketEpochMillis), ZoneOffset.UTC));

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
  }

  @Test
  public void shouldTransformMultipleTimeBucketsOrderedByTime() {
    // given — buckets inserted in reverse chronological order to verify sorting
    final long t1 = 1706800000000L;
    final long t2 = 1706800060000L;
    final long t3 = 1706800120000L;

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TIME,
            byTimeBucket(
                Map.of(
                    String.valueOf(t3), timeBucket(5, 0L, 4, 0L, 0, 0L),
                    String.valueOf(t1), timeBucket(10, 0L, 9, 0L, 1, 0L),
                    String.valueOf(t2), timeBucket(7, 0L, 6, 0L, 0, 0L)),
                "endCursor"));

    // when
    final var result = transformer.apply(input);

    // then — items must be ordered ascending by time
    assertThat(result.items()).hasSize(3);
    assertThat(result.items().get(0).time())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(t1), ZoneOffset.UTC));
    assertThat(result.items().get(1).time())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(t2), ZoneOffset.UTC));
    assertThat(result.items().get(2).time())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(t3), ZoneOffset.UTC));
  }

  @Test
  public void shouldHandleZeroCountsAndNullTimestamps() {
    // given
    final long bucketKey = 1706800000000L;
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TIME,
            byTimeBucket(
                Map.of(String.valueOf(bucketKey), timeBucket(0, null, 0, null, 0, null)), null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isNull();

    final var entity = result.items().get(0);
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandleMissingSubAggregations() {
    // given — bucket exists but has no sub-aggregations
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_TIME,
            byTimeBucket(Map.of("1706800000000", new AggregationResult(10L, Map.of())), null));

    // when
    final var result = transformer.apply(input);

    // then — entity with empty aggregations should be filtered out
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  public void shouldHandleNullByTimeAggregation() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_TIME, new AggregationResult(0L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  // ---- helpers ----

  private static AggregationResult byTimeBucket(
      final Map<String, AggregationResult> timeBuckets, final String endCursor) {
    return new AggregationResult(0L, timeBuckets, List.of(), endCursor);
  }

  private static AggregationResult timeBucket(
      final long createdCount,
      final Long createdTimestamp,
      final long completedCount,
      final Long completedTimestamp,
      final long failedCount,
      final Long failedTimestamp) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_CREATED, statusBucket(createdCount, createdTimestamp),
            AGGREGATION_COMPLETED, statusBucket(completedCount, completedTimestamp),
            AGGREGATION_FAILED, statusBucket(failedCount, failedTimestamp));
    return new AggregationResult(0L, subAggs);
  }

  private static AggregationResult statusBucket(final long count, final Long lastUpdatedAtMillis) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_COUNT,
            new AggregationResult(count, Map.of()),
            AGGREGATION_LAST_UPDATED_AT,
            new AggregationResult(lastUpdatedAtMillis, Map.of()));
    return new AggregationResult(0L, subAggs);
  }
}
