/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.transformers.SearchTransfomer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public class RangeQueryTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchRangeQuery, RangeQuery> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchRangeQuery.class);
  }

  @Test
  public void shouldApplyTransformer() {
    // given
    final var query = SearchQueryBuilders.range().field("foo").lt(12456L).build();

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isInstanceOf(RangeQuery.class);
    assertThat(result.lt().to(Long.class)).isEqualTo(12456L);
  }
}
