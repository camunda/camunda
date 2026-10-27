/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.VariableNameAggregation.AGGREGATION_NAME_BY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VariableNameAggregationResultTransformerTest {

  private final VariableNameAggregationResultTransformer transformer =
      new VariableNameAggregationResultTransformer();

  @Test
  void shouldExtractBucketKeysInOrder() {
    // given
    final var buckets = new LinkedHashMap<String, AggregationResult>();
    buckets.put("amount", new AggregationResult(2L, Map.of()));
    buckets.put("total", new AggregationResult(1L, Map.of()));
    final var byNameAgg = new AggregationResult(null, buckets);

    // when
    final var result = transformer.apply(Map.of(AGGREGATION_NAME_BY_NAME, byNameAgg));

    // then
    assertThat(result.items()).containsExactly("amount", "total");
  }

  @Test
  void shouldReturnEmptyListWhenAggregationMissing() {
    // when
    final var result = transformer.apply(Map.of());

    // then
    assertThat(result.items()).isEmpty();
  }
}
