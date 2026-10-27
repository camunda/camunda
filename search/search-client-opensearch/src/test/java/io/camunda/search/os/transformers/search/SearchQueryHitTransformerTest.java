/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.search.Hit;

public class SearchQueryHitTransformerTest {

  private final SearchQueryHitTransformer<Object> transformer =
      new SearchQueryHitTransformer<>(new OpensearchTransformers());

  @Test
  void shouldPreserveNullSortValueSoCursorCanResume() {
    // given a hit whose first sort value is null (e.g. a missing businessId) and a non-null
    // tie-breaker key
    final Hit<Object> hit =
        new Hit.Builder<>()
            .index("process-instance")
            .id("1")
            .sort(List.of(FieldValue.NULL, FieldValue.of(2251799813685249L)))
            .build();

    // when
    final var result = transformer.apply(hit);

    // then the raw sort values are preserved: the null stays null (not the literal string "null",
    // which would stall search_after across the null boundary) and the key keeps its numeric type
    assertThat(result.sortValues()).containsExactly(null, 2251799813685249L);
  }

  @Test
  void shouldReturnNullSortValuesWhenHitIsNotSorted() {
    // given a hit without sort values
    final Hit<Object> hit = new Hit.Builder<>().index("process-instance").id("1").build();

    // when / then
    assertThat(transformer.apply(hit).sortValues()).isNull();
  }
}
