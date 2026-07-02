/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.WaitStateStatisticsAggregation.AGGREGATION_GROUP_ELEMENTS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.entities.WaitStateStatisticsEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WaitStateStatisticsAggregationResultTransformerTest {

  private final WaitStateStatisticsAggregationResultTransformer transformer =
      new WaitStateStatisticsAggregationResultTransformer();

  @Test
  void shouldReturnEmptyWhenNoAggregations() {
    // when
    final var result = transformer.apply(Map.of());

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldMapBucketsToEntities() {
    // given
    final Map<String, AggregationResult> input =
        Map.of(
            AGGREGATION_GROUP_ELEMENTS,
            new AggregationResult.Builder()
                .aggregations(
                    Map.of(
                        "task-a", new AggregationResult.Builder().docCount(3L).build(),
                        "task-b", new AggregationResult.Builder().docCount(1L).build()))
                .build());

    // when
    final var result = transformer.apply(input);

    // then
    assertThat(result.items())
        .containsExactlyInAnyOrder(
            new WaitStateStatisticsEntity("task-a", 3L),
            new WaitStateStatisticsEntity("task-b", 1L));
  }
}
