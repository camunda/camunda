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
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

public class SearchTermsAggregatorTransformerTest
    extends AbstractSearchAggregatorTransformerTest<SearchTermsAggregator> {

  private static Stream<Arguments> provideAggregations() {
    return Stream.of(
        Arguments.arguments(
            SearchAggregatorBuilders.terms()
                .name("name")
                .field("name")
                .size(10)
                .minDocCount(1)
                .build(),
            "{'terms':{'field':'name','min_doc_count':1,'size':10}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.terms()
                .name("name")
                .field("category")
                .size(5)
                .minDocCount(0)
                .build(),
            "{'terms':{'field':'category','min_doc_count':0,'size':5}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.terms()
                .name("name")
                .field("status")
                .size(20)
                .minDocCount(3)
                .build(),
            "{'terms':{'field':'status','min_doc_count':3,'size':20}}"),
        Arguments.arguments(
            SearchAggregatorBuilders.terms()
                .name("name")
                .field("status")
                .size(20)
                .minDocCount(3)
                .aggregations(SearchAggregatorBuilders.terms("termsAgg", "field"))
                .build(),
            "{'aggregations':{'termsAgg':{'terms':{'field':'field','min_doc_count':1,'size':10}}},'terms':{'field':'status','min_doc_count':3,'size':20}}"));
  }

  @Test
  public void shouldThrowErrorOnNullName() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.terms().build())
        .hasMessageContaining("Expected non-null field for name.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchAggregatorBuilders.terms().name("name").size(10).build())
        .hasMessageContaining("Expected non-null field for field.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnInvalidSize() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchAggregatorBuilders.terms().name("name").field("name").size(-1).build())
        .hasMessageContaining("Size must be a positive integer.")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Override
  protected Class<SearchTermsAggregator> getTransformerClass() {
    return SearchTermsAggregator.class;
  }
}
