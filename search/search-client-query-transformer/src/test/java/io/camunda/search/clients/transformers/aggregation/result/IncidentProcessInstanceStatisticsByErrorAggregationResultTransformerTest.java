/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_AFFECTED_INSTANCES;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_BY_ERROR;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_ERROR_HASH;
import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByErrorAggregation.AGGREGATION_NAME_TOTAL_ESTIMATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.aggregation.result.IncidentProcessInstanceStatisticsByErrorAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class IncidentProcessInstanceStatisticsByErrorAggregationResultTransformerTest {

  private final IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer transformer =
      new IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer();

  @Test
  public void shouldReturnEmptyResultWhenByErrorAggregationMissing() {
    // given
    final Map<String, AggregationResult> input = Map.of();

    // when
    final IncidentProcessInstanceStatisticsByErrorAggregationResult result =
        transformer.apply(input);

    // then
    assertThat(result.items()).isEmpty();
    assertThat(result.totalItems()).isZero();
  }

  @Test
  public void shouldTransformBucketsAndUseParsedErrorHashKey() {
    // given
    final Map<String, AggregationResult> perErrorBuckets =
        new LinkedHashMap<>(
            Map.of(
                "Something failed", errorBucket("123", 5L), "Other error", errorBucket("456", 0L)));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.totalItems()).isEqualTo(2);
    assertThat(result.items())
        .containsExactlyInAnyOrder(
            new IncidentProcessInstanceStatisticsByErrorEntity(123, "Something failed", 5L),
            new IncidentProcessInstanceStatisticsByErrorEntity(456, "Other error", 0L));
  }

  @Test
  public void shouldHandleNullErrorMessageBucketKey() {
    // given
    final Map<String, AggregationResult> perErrorBuckets =
        Map.of(
            IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer.NULL,
            errorBucket("999", 2L));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items())
        .containsExactly(new IncidentProcessInstanceStatisticsByErrorEntity(999, null, 2L));
  }

  @Test
  public void shouldDefaultAffectedInstancesToZeroWhenMissing() {
    // given
    final Map<String, AggregationResult> perErrorBuckets =
        Map.of("Missing affected instances", errorBucketWithOnlyHash("101"));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items())
        .containsExactly(
            new IncidentProcessInstanceStatisticsByErrorEntity(
                101, "Missing affected instances", 0L));
  }

  @Test
  public void shouldUseTotalEstimateWhenProvided() {
    // given
    final Map<String, AggregationResult> perErrorBuckets = Map.of("Error", errorBucket("1", 1L));

    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_NAME_BY_ERROR,
            agg(0L, perErrorBuckets),
            AGGREGATION_NAME_TOTAL_ESTIMATE,
            new AggregationResult(100L, Map.of()));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.totalItems()).isEqualTo(100);
  }

  @Test
  public void shouldThrowWhenErrorHashAggregationMissing() {
    // given
    final Map<String, AggregationResult> bucketSubAggs =
        Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, new AggregationResult(1L, Map.of()));

    final Map<String, AggregationResult> perErrorBuckets =
        Map.of("Error", new AggregationResult(0L, bucketSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when + then
    assertThatThrownBy(() -> transformer.apply(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing required error hash aggregation");
  }

  @Test
  public void shouldThrowWhenErrorHashAggregationIsEmpty() {
    // given
    final Map<String, AggregationResult> bucketSubAggs =
        Map.of(
            AGGREGATION_NAME_ERROR_HASH, new AggregationResult(0L, Map.of()),
            AGGREGATION_NAME_AFFECTED_INSTANCES, new AggregationResult(1L, Map.of()));

    final Map<String, AggregationResult> perErrorBuckets =
        Map.of("Error", new AggregationResult(0L, bucketSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when + then
    assertThatThrownBy(() -> transformer.apply(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing required error hash aggregation");
  }

  @Test
  public void shouldThrowWhenHashBucketKeyIsNotAnInteger() {
    // given
    final Map<String, AggregationResult> hashBuckets =
        Map.of("not-an-int", AggregationResult.EMPTY);

    final Map<String, AggregationResult> bucketSubAggs =
        Map.of(
            AGGREGATION_NAME_ERROR_HASH,
            agg(0L, hashBuckets),
            AGGREGATION_NAME_AFFECTED_INSTANCES,
            new AggregationResult(1L, Map.of()));

    final Map<String, AggregationResult> perErrorBuckets =
        Map.of("Error", new AggregationResult(0L, bucketSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when + then
    assertThatThrownBy(() -> transformer.apply(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to parse error hash bucket key to integer");
  }

  @Test
  public void shouldFallbackToErrorMessageHashCodeWhenAllHashBucketKeysNull() {
    // given
    final String errorMessage = "Some error";

    final Map<String, AggregationResult> hashBuckets = new HashMap<>();
    hashBuckets.put(null, AggregationResult.EMPTY);

    final Map<String, AggregationResult> bucketSubAggs =
        Map.of(
            AGGREGATION_NAME_ERROR_HASH,
            agg(0L, hashBuckets),
            AGGREGATION_NAME_AFFECTED_INSTANCES,
            new AggregationResult(3L, Map.of()));

    final Map<String, AggregationResult> perErrorBuckets =
        Map.of(errorMessage, new AggregationResult(0L, bucketSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items())
        .containsExactly(
            new IncidentProcessInstanceStatisticsByErrorEntity(
                errorMessage.hashCode(), errorMessage, 3L));
  }

  @Test
  public void shouldThrowWhenUnableToResolveErrorHashCode() {
    // given
    final Map<String, AggregationResult> hashBuckets = new HashMap<>();
    hashBuckets.put(null, AggregationResult.EMPTY);

    final Map<String, AggregationResult> bucketSubAggs =
        Map.of(
            AGGREGATION_NAME_ERROR_HASH,
            agg(0L, hashBuckets),
            AGGREGATION_NAME_AFFECTED_INSTANCES,
            new AggregationResult(1L, Map.of()));

    final Map<String, AggregationResult> perErrorBuckets =
        Map.of(
            IncidentProcessInstanceStatisticsByErrorAggregationResultTransformer.NULL,
            new AggregationResult(0L, bucketSubAggs));

    final Map<String, AggregationResult> input =
        Map.of(AGGREGATION_NAME_BY_ERROR, agg(0L, perErrorBuckets));

    // when + then
    assertThatThrownBy(() -> transformer.apply(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Failed to resolve error hash code");
  }

  private static AggregationResult errorBucket(
      final String errorHashBucketKey, final long affected) {
    final Map<String, AggregationResult> subAggs =
        Map.of(
            AGGREGATION_NAME_ERROR_HASH,
            errorHashAgg(errorHashBucketKey),
            AGGREGATION_NAME_AFFECTED_INSTANCES,
            new AggregationResult(affected, Map.of()));

    return new AggregationResult(0L, subAggs);
  }

  private static AggregationResult errorBucketWithOnlyHash(final String errorHashBucketKey) {
    final Map<String, AggregationResult> subAggs =
        Map.of(AGGREGATION_NAME_ERROR_HASH, errorHashAgg(errorHashBucketKey));
    return new AggregationResult(0L, subAggs);
  }

  private static AggregationResult errorHashAgg(final String errorHashBucketKey) {
    final Map<String, AggregationResult> hashBuckets =
        Map.of(errorHashBucketKey, AggregationResult.EMPTY);
    return agg(0L, hashBuckets);
  }

  private static AggregationResult agg(
      final long docCount, final Map<String, AggregationResult> aggs) {
    return new AggregationResult(docCount, aggs);
  }
}
