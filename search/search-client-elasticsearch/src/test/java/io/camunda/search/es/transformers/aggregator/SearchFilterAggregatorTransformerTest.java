/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchFilterAggregator;
import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchFilterAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchFilterAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.filter()
                .name("name")
                .query(SearchQueryBuilders.term("key", 123L))
                .build(),
            "{'filter':{'term':{'key':{'value':123}}}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.filter()
                .name("filterAgg")
                .query(SearchQueryBuilders.term("key", 123L))
                .aggregations(
                    SearchAggregatorBuilders.filter(
                        "filterSubAgg", SearchQueryBuilders.gt("val", 5)))
                .build(),
            "{'aggregations':{'filterSubAgg':{'filter':{'range':{'val':{'gt':5}}}}},'filter':{'term':{'key':{'value':123}}}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.filter().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQuery() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.filter().name("name").build())
        .hasMessageContaining("Expected non-null field for query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchFilterAggregator> getTransformerClass() {
    return SearchFilterAggregator.class;
  }
}
