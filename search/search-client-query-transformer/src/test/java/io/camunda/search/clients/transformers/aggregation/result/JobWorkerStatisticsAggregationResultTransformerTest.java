/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_BY_WORKER;
import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_COMPLETED;
import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_COUNT;
import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_CREATED;
import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_FAILED;
import static io.camunda.search.aggregation.JobWorkerStatisticsAggregation.AGGREGATION_LAST_UPDATED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JobWorkerStatisticsAggregationResultTransformerTest {

  private final JobWorkerStatisticsAggregationResultTransformer transformer =
      new JobWorkerStatisticsAggregationResultTransformer();

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
  public void shouldTransformSingleWorkerStatistics() {
    // given
    final String worker = "worker-1";
    final long createdCount = 400L;
    final long completedCount = 390L;
    final long failedCount = 2L;
    final long createdTimestamp = 1706800000000L;
    final long completedTimestamp = 1706800100000L;
    final long failedTimestamp = 1706800200000L;
    final String endCursor = "cursorABC";

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(
                Map.of(
                    worker,
                    workerBucket(
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
    assertThat(entity.worker()).isEqualTo(worker);

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
  public void shouldTransformMultipleWorkerStatistics() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(
                Map.of(
                    "worker-1", workerBucket(400, 1000L, 390, 2000L, 2, 3000L),
                    "worker-2", workerBucket(350, 4000L, 340, 5000L, 5, 6000L),
                    "worker-3", workerBucket(100, 7000L, 100, 8000L, 0, 0L)),
                "endCursorXYZ"));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting("worker")
        .containsExactlyInAnyOrder("worker-1", "worker-2", "worker-3");
    assertThat(result.endCursor()).isEqualTo("endCursorXYZ");
  }

  @Test
  public void shouldHandleZeroCountsAndNullTimestamps() {
    // given
    final String worker = "idle-worker";
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(Map.of(worker, workerBucket(0, null, 0, null, 0, null)), null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isNull();

    final var entity = result.items().get(0);
    assertThat(entity.worker()).isEqualTo(worker);
    assertThat(entity.created().count()).isZero();
    assertThat(entity.created().lastUpdatedAt()).isNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandleMissingSubAggregations() {
    // given - bucket exists but has no sub-aggregations
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(Map.of("missing-subs", new AggregationResult(10L, Map.of())), null));

    // when
    final var result = transformer.apply(input);

    // then - entity with empty aggregations should be filtered out
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  public void shouldHandlePartialSubAggregations() {
    // given - only created aggregation is present
    final String worker = "partial-worker";
    final Map<String, AggregationResult> subAggs =
        Map.of(AGGREGATION_CREATED, statusBucket(25L, 1706800000000L));

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(Map.of(worker, new AggregationResult(0L, subAggs)), "cursor"));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isEqualTo("cursor");

    final var entity = result.items().get(0);
    assertThat(entity.worker()).isEqualTo(worker);
    assertThat(entity.created().count()).isEqualTo(25L);
    assertThat(entity.created().lastUpdatedAt()).isNotNull();
    assertThat(entity.completed().count()).isZero();
    assertThat(entity.completed().lastUpdatedAt()).isNull();
    assertThat(entity.failed().count()).isZero();
    assertThat(entity.failed().lastUpdatedAt()).isNull();
  }

  @Test
  public void shouldHandleNullDocCountInSubAggregations() {
    // given
    final String worker = "null-count-worker";
    final Map<String, AggregationResult> statusSubAggs =
        Map.of(
            AGGREGATION_COUNT, new AggregationResult(null, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(null, Map.of()));

    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_CREATED, new AggregationResult(10L, statusSubAggs),
            AGGREGATION_COMPLETED, new AggregationResult(10L, statusSubAggs),
            AGGREGATION_FAILED, new AggregationResult(10L, statusSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_WORKER,
            byWorkerBucket(Map.of(worker, new AggregationResult(0L, subAggs)), null));

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
  }

  @Test
  public void shouldHandleNullByWorkerAggregation() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_WORKER, new AggregationResult(0L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  // ---- helpers ----

  private static AggregationResult byWorkerBucket(
      final Map<String, AggregationResult> workerBuckets, final String endCursor) {
    return new AggregationResult(0L, workerBuckets, List.of(), endCursor);
  }

  private static AggregationResult workerBucket(
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
            AGGREGATION_COUNT, new AggregationResult(count, Map.of()),
            AGGREGATION_LAST_UPDATED_AT, new AggregationResult(lastUpdatedAtMillis, Map.of()));

    return new AggregationResult(0L, subAggs);
  }
}
