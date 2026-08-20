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
import io.camunda.search.clients.aggregator.SearchMaxAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchMaxAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchMaxAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.max().name("name").field("field1").build(),
            "{'max':{'field':'field1'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.max()
                .name("maxAgg")
                .field("field1")
                .aggregations(SearchAggregatorBuilders.max("maxSubAgg", "field2"))
                .build(),
            "{'aggregations':{'maxSubAgg':{'max':{'field':'field2'}}},'max':{'field':'field1'}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.max().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.max().name("name").build())
        .hasMessageContaining("Expected non-null field for field.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchMaxAggregator> getTransformerClass() {
    return SearchMaxAggregator.class;
  }
}
