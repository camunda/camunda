/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_BY_ERROR;
import static io.camunda.search.aggregation.JobErrorStatisticsAggregation.AGGREGATION_WORKERS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JobErrorStatisticsAggregationResultTransformerTest {

  private final JobErrorStatisticsAggregationResultTransformer transformer =
      new JobErrorStatisticsAggregationResultTransformer();

  @Test
  void shouldReturnEmptyListWhenAggregationsEmpty() {
    // when
    final var result = transformer.apply(Map.of());

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  void shouldReturnEmptyListWhenByErrorAggIsNull() {
    // given
    final Map<String, AggregationResult> input = Map.of();

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.endCursor()).isNull();
  }

  @Test
  void shouldTransformSingleErrorBucket() {
    // given
    final long workerCount = 7L;
    final String endCursor = "cursorABC";

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_BY_ERROR,
            byErrorBucket(
                Map.of("UNHANDLED_ERROR_EVENT__Something went wrong", workersBucket(workerCount)),
                endCursor));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.endCursor()).isEqualTo(endCursor);

    final var entity = result.items().get(0);
    assertThat(entity.errorCode()).isEqualTo("UNHANDLED_ERROR_EVENT");
    assertThat(entity.errorMessage()).isEqualTo("Something went wrong");
    assertThat(entity.workers()).isEqualTo((int) workerCount);
  }

  @Test
  void shouldTransformMultipleErrorBuckets() {
    // given
    final Map<String, AggregationResult> buckets = new LinkedHashMap<>();
    buckets.put("IO_ERROR__Disk full", workersBucket(3L));
    buckets.put("TIMEOUT__Connection timed out", workersBucket(12L));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_ERROR, byErrorBucket(buckets, null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.endCursor()).isNull();

    assertThat(result.items())
        .anySatisfy(
            e -> {
              assertThat(e.errorCode()).isEqualTo("IO_ERROR");
              assertThat(e.errorMessage()).isEqualTo("Disk full");
              assertThat(e.workers()).isEqualTo(3);
            });
    assertThat(result.items())
        .anySatisfy(
            e -> {
              assertThat(e.errorCode()).isEqualTo("TIMEOUT");
              assertThat(e.errorMessage()).isEqualTo("Connection timed out");
              assertThat(e.workers()).isEqualTo(12);
            });
  }

  @Test
  void shouldHandleMissingWorkersSubAggregation() {
    // given
    final Map<String, AggregationResult> buckets = Map.of("NO_WORKERS_ERROR__msg", emptyBucket());

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_ERROR, byErrorBucket(buckets, null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).workers()).isEqualTo(0);
  }

  @Test
  void shouldHandleBucketWithErrorCodeOnly() {
    // given — composite key has errorCode but an empty errorMessage
    final Map<String, AggregationResult> buckets = Map.of("IO_ERROR__", workersBucket(5L));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_BY_ERROR, byErrorBucket(buckets, null));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    final var entity = result.items().getFirst();
    assertThat(entity.errorCode()).isEqualTo("IO_ERROR");
    assertThat(entity.errorMessage()).isEmpty();
    assertThat(entity.workers()).isEqualTo(5);
  }

  // ---------- helpers ----------

  private static AggregationResult byErrorBucket(
      final Map<String, AggregationResult> buckets, final String endCursor) {
    return new AggregationResult.Builder().aggregations(buckets).endCursor(endCursor).build();
  }

  private static AggregationResult workersBucket(final long count) {
    return new AggregationResult.Builder()
        .aggregations(
            Map.of(AGGREGATION_WORKERS, new AggregationResult.Builder().docCount(count).build()))
        .build();
  }

  private static AggregationResult emptyBucket() {
    return new AggregationResult.Builder().aggregations(Collections.emptyMap()).build();
  }
}
