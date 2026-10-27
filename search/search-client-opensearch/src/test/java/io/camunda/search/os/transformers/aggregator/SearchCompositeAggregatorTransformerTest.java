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
import io.camunda.search.clients.aggregator.SearchCompositeAggregator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchCompositeAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchCompositeAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.composite()
                .name("name")
                .size(10)
                .sources(List.of(SearchAggregatorBuilders.terms("processId", "processId")))
                .build(),
            "{'composite':{'size':10,'sources':[{'processId':{'terms':{'field':'processId'}}}]}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.composite()
                .name("name")
                .size(5)
                .sources(
                    List.of(
                        SearchAggregatorBuilders.terms("processId", "processId"),
                        SearchAggregatorBuilders.terms("tenantId", "tenantId")))
                .build(),
            "{'composite':{'size':5,'sources':[{'processId':{'terms':{'field':'processId'}}},{'tenantId':{'terms':{'field':'tenantId'}}}]}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.composite()
                .name("name")
                .size(20)
                .sources(List.of(SearchAggregatorBuilders.terms("processId", "processId")))
                .aggregations(SearchAggregatorBuilders.terms("termsAgg", "field"))
                .build(),
            "{'aggregations':{'termsAgg':{'terms':{'field':'field','min_doc_count':1,'size':10}}},'composite':{'size':20,'sources':[{'processId':{'terms':{'field':'processId'}}}]}}"));
  }

  @Test
  public void shouldThrowErrorOnNullSources() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.composite().name("name").build())
        .hasMessageContaining("Expected non-null field for sources.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.composite().sources(List.of()).build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnInvalidSize() {
    // given

    // when - throw
    assertThatThrownBy(
            () ->
                SearchAggregatorBuilders.composite()
                    .name("name")
                    .sources(List.of())
                    .size(-1)
                    .build())
        .hasMessageContaining("Size must be a positive integer.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Override
  protected Class<SearchCompositeAggregator> getTransformerClass() {
    return SearchCompositeAggregator.class;
  }
}
