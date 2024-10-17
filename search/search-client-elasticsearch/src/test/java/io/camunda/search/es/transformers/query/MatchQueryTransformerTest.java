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
import io.camunda.search.clients.query.SearchMatchQuery.SearchMatchQueryOperator;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MatchQueryTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchQuery.class);
  }

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.match("foo", "ba", SearchMatchQueryOperator.AND),
            "Query: {'match':{'foo':{'operator':'and','query':'ba'}}}"),
        Arguments.arguments(
            SearchQueryBuilders.match()
                .field("foo")
                .operator(SearchMatchQueryOperator.AND)
                .query("ba")
                .build()
                .toSearchQuery(),
            "Query: {'match':{'foo':{'operator':'and','query':'ba'}}}"),
        Arguments.arguments(
            SearchQueryBuilders.match("foo", "ba", SearchMatchQueryOperator.OR),
            "Query: {'match':{'foo':{'operator':'or','query':'ba'}}}"),
        Arguments.arguments(
            SearchQueryBuilders.match()
                .field("foo")
                .operator(SearchMatchQueryOperator.OR)
                .query("ba")
                .build()
                .toSearchQuery(),
            "Query: {'match':{'foo':{'operator':'or','query':'ba'}}}"),
        Arguments.arguments(SearchQueryBuilders.matchNone(), "Query: {'match_none':{}}"),
        Arguments.arguments(SearchQueryBuilders.matchAll(), "Query: {'match_all':{}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueries")
  public void shouldApplyTransformer(final SearchQuery query, final String expectedResultQuery) {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isNotNull();
    assertThat(result.toString()).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldThrowErrorOnNullQuery() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.match().field("foo").build())
        .hasMessageContaining(
            "Expected a non-null query parameter for the match query, with field: 'foo'.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQueryFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.match("foo", null, null))
        .hasMessageContaining(
            "Expected a non-null query parameter for the match query, with field: 'foo'.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.match().build())
        .hasMessageContaining("Expected a non-null field for the match query.")
        .isInstanceOf(NullPointerException.class);
  }
}
