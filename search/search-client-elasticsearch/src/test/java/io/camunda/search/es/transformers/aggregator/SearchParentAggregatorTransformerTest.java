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
import io.camunda.search.clients.aggregator.SearchParentAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchParentAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchParentAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.parent().name("name").type("name").build(),
            "{'parent':{'type':'name'}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.parent()
                .name("parentAgg")
                .type("activity")
                .aggregations(SearchAggregatorBuilders.parent("parentSubAgg", "activity"))
                .build(),
            "{'aggregations':{'parentSubAgg':{'parent':{'type':'activity'}}},'parent':{'type':'activity'}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // when - then
    assertThatThrownBy(() -> SearchAggregatorBuilders.parent().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullType() {
    // when/then
    assertThatThrownBy(() -> SearchAggregatorBuilders.parent().name("name").build())
        .hasMessageContaining("Expected non-null field for type.")
        .isInstanceOf(NullPointerException.class);
  }

  @Override
  protected Class<SearchParentAggregator> getTransformerClass() {
    return SearchParentAggregator.class;
  }
}
