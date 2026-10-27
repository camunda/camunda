/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SearchAggregatorBuildersTest {

  public static final List<SearchAggregator> SUB_AGGREGATORS =
      List.of(
          SearchAggregatorBuilders.terms("sub1", "field"),
          SearchAggregatorBuilders.children("sub2", "type"));

  @Test
  public void shouldBuildFilterAggregator() {
    // given
    final var query = SearchQueryBuilders.term("field", "value");
    final var aggregator =
        SearchAggregatorBuilders.filter()
            .name("aggregate")
            .query(query)
            .aggregations(SUB_AGGREGATORS)
            .build();

    // then
    assertThat(aggregator.name()).isEqualTo("aggregate");
    assertThat(aggregator.query()).isEqualTo(query);
    assertThat(aggregator.aggregations()).containsExactlyElementsOf(SUB_AGGREGATORS);
  }

  @Test
  public void shouldBuildFiltersAggregator() {
    // given
    final var query = SearchQueryBuilders.term("field", "value");
    final var aggregator =
        SearchAggregatorBuilders.filters()
            .name("aggregate")
            .namedQuery("q1", query)
            .namedQuery("q2", query)
            .aggregations(SUB_AGGREGATORS)
            .build();

    // then
    assertThat(aggregator.name()).isEqualTo("aggregate");
    assertThat(aggregator.queries()).containsExactly(entry("q1", query), entry("q2", query));
    assertThat(aggregator.aggregations()).containsExactlyElementsOf(SUB_AGGREGATORS);
  }

  @Test
  public void shouldBuildChildrenAggregator() {
    // given
    final var aggregator =
        SearchAggregatorBuilders.children()
            .name("aggregate")
            .type("type")
            .aggregations(SUB_AGGREGATORS)
            .build();

    // then
    assertThat(aggregator.name()).isEqualTo("aggregate");
    assertThat(aggregator.type()).isEqualTo("type");
    assertThat(aggregator.aggregations()).containsExactlyElementsOf(SUB_AGGREGATORS);
  }
}
