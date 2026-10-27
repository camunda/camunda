/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchAggregatorBuilders;
import io.camunda.search.clients.aggregator.SearchChildrenAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchChildrenAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchChildrenAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.children().name("name").type("name").build(),
            "{'children':{'type':'name'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.children()
                .name("childrenAgg")
                .type("activity")
                .aggregations(SearchAggregatorBuilders.children("childrenSubAgg", "activity"))
                .build(),
            "{'aggregations':{'childrenSubAgg':{'children':{'type':'activity'}}},'children':{'type':'activity'}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.children().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullType() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.children().name("name").build())
        .hasMessageContaining("Expected non-null field for type.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchChildrenAggregator> getTransformerClass() {
    return SearchChildrenAggregator.class;
  }
}
