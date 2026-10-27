/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_INCOMPLETE;
import static io.camunda.search.aggregation.GlobalJobStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class GlobalJobStatisticsAggregationResultTransformerTest {

  private final GlobalJobStatisticsAggregationResultTransformer transformer =
      new GlobalJobStatisticsAggregationResultTransformer();

  @Test
  public void shouldReturnZeroCountsWhenAggregationsEmpty() {
    // given
    final Map<String, AggregationResult> input = Map.of();

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
    assertThat(entity.isIncomplete()).isFalse();
  }

  @Test
  public void shouldTransformAllStatusMetrics() {
    // given
    final long createdCount = 100L;
    final long completedCount = 80L;
    final long failedCount = 5L;
    final long createdTimestamp = 1706800000000L;
    final long completedTimestamp = 1706800100000L;
    final long failedTimestamp = 1706800200000L;

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_CREATED, statusBucket(createdCount, createdTimestamp),
            AGGREGATION_COMPLETED, statusBucket(completedCount, completedTimestamp),
            AGGREGATION_FAILED, statusBucket(failedCount, failedTimestamp),
            AGGREGATION_INCOMPLETE, new AggregationResult(0L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();

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

    assertThat(entity.isIncomplete()).isFalse();
  }

  @Test
  public void shouldSetIncompleteToTrueWhenMaxIncompleteGreaterThanZero() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_CREATED, statusBucket(10L, 0L),
            AGGREGATION_COMPLETED, statusBucket(5L, 0L),
            AGGREGATION_FAILED, statusBucket(1L, 0L),
            AGGREGATION_INCOMPLETE, new AggregationResult(1L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.entity().isIncomplete()).isTrue();
  }

  @Test
  public void shouldHandleMissingSubAggregations() {
    // given - bucket exists but has no sub-aggregations
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_CREATED, new AggregationResult(10L, null));

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandleZeroTimestampAsNull() {
    // given - timestamp is 0, should be treated as null
    final Map<String, AggregationResult> input = Map.of(AGGREGATION_CREATED, statusBucket(50L, 0L));

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();
    assertThat(entity.created().count()).isEqualTo(50L);
    assertThat(entity.created().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandleNullDocCountInSubAggregations() {
    // given
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_COUNT, new AggregationResult(null, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(null, Map.of()));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_CREATED, new AggregationResult(10L, subAggs));

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandlePartialAggregations() {
    // given - only created aggregation is present
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_CREATED, statusBucket(25L, 1706800000000L));

    // when
    final var result = transformer.apply(input);

    // then
    final var entity = result.entity();
    assertThat(entity.created().count()).isEqualTo(25L);
    assertThat(entity.created().lastUpdatedAt()).isNotNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
  }

  private static AggregationResult statusBucket(final long count, final long lastUpdatedAtMillis) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_COUNT, new AggregationResult(count, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(lastUpdatedAtMillis, Map.of()));

    return new AggregationResult(0L, subAggs);
  }
}
