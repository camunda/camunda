/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TermsQueryTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchQuery.class);
  }

  private static Stream<Arguments> provideRangeQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.intTerms("foo", List.of(1, 2)), "Query: {'terms':{'foo':[1,2]}}"),
        Arguments.arguments(
            SearchQueryBuilders.longTerms("foo", List.of(1L, 2L)),
            "Query: {'terms':{'foo':[1,2]}}"),
        Arguments.arguments(
            SearchQueryBuilders.stringTerms("foo", List.of("ele1", "ele2")),
            "Query: {'terms':{'foo':['ele1','ele2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.terms()
                .field("foo")
                .stringTerms(Arrays.stream(new String[] {"ele1", null}).toList())
                .build()
                .toSearchQuery(),
            "Query: {'terms':{'foo':['ele1',null]}}"),
        Arguments.arguments(
            SearchQueryBuilders.terms()
                .field("foo")
                .stringTerms(List.of("ele1", "ele2"))
                .build()
                .toSearchQuery(),
            "Query: {'terms':{'foo':['ele1','ele2']}}"));
  }

  @ParameterizedTest
  @MethodSource("provideRangeQueries")
  public void shouldApplyTransformer(
      final SearchQuery rangeQuery, final String expectedResultQuery) {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(rangeQuery);

    // then
    assertThat(result).isNotNull();
    assertThat(result.toString()).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldThrowErrorOnNullValue() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.terms().field("foo").stringTerms(null).build())
        .hasMessageContaining("Expected non-null values collection, for typed values.")
        .isInstanceOf(IllegalArgumentException.class);
  }
}
