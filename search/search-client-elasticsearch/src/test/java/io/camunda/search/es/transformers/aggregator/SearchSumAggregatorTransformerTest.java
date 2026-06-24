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
import io.camunda.search.clients.aggregator.SearchSumAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchSumAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchSumAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.sum().name("name").field("field1").build(),
            "{'sum':{'field':'field1'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.sum()
                .name("sumAgg")
                .field("field1")
                .aggregations(SearchAggregatorBuilders.sum("sumSubAgg", "field2"))
                .build(),
            "{'aggregations':{'sumSubAgg':{'sum':{'field':'field2'}}},'sum':{'field':'field1'}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.sum().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.sum().name("name").build())
        .hasMessageContaining("Expected non-null field for field.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchSumAggregator> getTransformerClass() {
    return SearchSumAggregator.class;
  }
}
