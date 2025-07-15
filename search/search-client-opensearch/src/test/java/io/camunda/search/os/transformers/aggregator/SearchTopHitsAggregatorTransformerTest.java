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
import io.camunda.search.clients.aggregator.SearchTopHitsAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchTopHitsAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchTopHitsAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.topHits().name("name").field("name").size(10).build(),
            "{'top_hits':{'size':10,'sort':[{'name':{'order':'desc'}}]}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.topHits().name("name").field("category").size(5).build(),
            "{'top_hits':{'size':5,'sort':[{'category':{'order':'desc'}}]}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.topHits()
                .name("name")
                .field("status")
                .size(20)
                .aggregations(SearchAggregatorBuilders.terms("termsAgg", "field"))
                .build(),
            "{'aggregations':{'termsAgg':{'terms':{'field':'field','min_doc_count':1,'size':10}}},'top_hits':{'size':20,'sort':[{'status':{'order':'desc'}}]}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.topHits().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.topHits().name("name").size(10).build())
        .hasMessageContaining("Expected non-null field for field.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnInvalidSize() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchAggregatorBuilders.topHits().name("name").field("name").size(-1).build())
        .hasMessageContaining("Size must be a positive integer.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Override
  protected Class<SearchTopHitsAggregator> getTransformerClass() {
    return SearchTopHitsAggregator.class;
  }
}
