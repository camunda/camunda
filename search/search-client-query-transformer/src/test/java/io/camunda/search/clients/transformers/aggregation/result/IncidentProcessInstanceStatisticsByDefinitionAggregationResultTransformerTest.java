/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.IncidentProcessInstanceStatisticsByDefinitionAggregation.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.core.AggregationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformerTest {

  private final IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformer
      transformer = new IncidentProcessInstanceStatisticsByDefinitionAggregationResultTransformer();

  @Test
  void shouldBuildAggregationResultFromBucketsAndTotalEstimate() {
    // given
    final var buckets =
        Map.of(
            "101",
            agg(0L, Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, agg(3L))),
            "202",
            agg(0L, Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, agg(7L))));

    final var input =
        Map.of(
            AGGREGATION_NAME_BY_DEFINITION, agg(0L, buckets),
            AGGREGATION_NAME_TOTAL_ESTIMATE, agg(42L));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.totalItems()).isEqualTo(42);
    assertThat(result.items())
        .hasSize(2)
        .anySatisfy(
            item -> {
              assertThat(item.processDefinitionKey()).isEqualTo(101L);
              assertThat(item.tenantId()).isNull();
              assertThat(item.activeInstancesWithErrorCount()).isEqualTo(3L);
              assertThat(item.processDefinitionId()).isNull();
              assertThat(item.processDefinitionName()).isNull();
              assertThat(item.processDefinitionVersion()).isNull();
            })
        .anySatisfy(
            item -> {
              assertThat(item.processDefinitionKey()).isEqualTo(202L);
              assertThat(item.tenantId()).isNull();
              assertThat(item.activeInstancesWithErrorCount()).isEqualTo(7L);
              assertThat(item.processDefinitionId()).isNull();
              assertThat(item.processDefinitionName()).isNull();
              assertThat(item.processDefinitionVersion()).isNull();
            });
  }

  @Test
  void shouldFallbackToBucketCountWhenTotalEstimateIsMissing() {
    // given
    final Map<String, AggregationResult> buckets = new LinkedHashMap<>();
    buckets.put("101", agg(0L, Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, agg(3L))));
    buckets.put("202", agg(0L, Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, agg(7L))));

    final var input = Map.of(AGGREGATION_NAME_BY_DEFINITION, agg(0L, buckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.totalItems()).isEqualTo(2);
    assertThat(result.items()).hasSize(2);
  }

  @Test
  void shouldDefaultAffectedInstancesToZeroWhenSubAggregationIsMissing() {
    // given
    final var buckets = Map.of("101", agg(0L, Map.of()));

    final var input = Map.of(AGGREGATION_NAME_BY_DEFINITION, agg(0L, buckets));

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.totalItems()).isEqualTo(1);
    assertThat(result.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.processDefinitionKey()).isEqualTo(101L);
              assertThat(item.tenantId()).isNull();
              assertThat(item.activeInstancesWithErrorCount()).isEqualTo(0L);
            });
  }

  @Test
  void shouldFailWhenBucketKeyIsMissingOrInvalid() {
    // missing/blank bucket key
    assertThatThrownBy(() -> transformer.apply(inputWithBucketKey("")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing required bucket key");

    // invalid processDefinitionKey
    assertThatThrownBy(() -> transformer.apply(inputWithBucketKey("abc")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid processDefinitionKey");
  }

  private static Map<String, AggregationResult> inputWithBucketKey(final String bucketKey) {
    return Map.of(
        AGGREGATION_NAME_BY_DEFINITION,
        agg(0L, Map.of(bucketKey, agg(0L, Map.of(AGGREGATION_NAME_AFFECTED_INSTANCES, agg(1L))))));
  }

  private static AggregationResult agg(final long docCount) {
    return agg(docCount, Map.of());
  }

  private static AggregationResult agg(
      final long docCount, final Map<String, AggregationResult> aggregations) {
    return new AggregationResult(docCount, aggregations);
  }
}
