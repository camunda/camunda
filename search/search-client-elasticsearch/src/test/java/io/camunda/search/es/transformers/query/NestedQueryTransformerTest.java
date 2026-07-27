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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class NestedQueryTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchQuery.class);
  }

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.nestedQuery(
                "foo.bar", SearchQueryBuilders.term("foo.bar.field", "val")),
            "Query: {'nested':{'path':'foo.bar','query':{'term':{'foo.bar.field':{'value':'val'}}},'score_mode':'none'}}"),
        Arguments.arguments(
            SearchQueryBuilders.nested(
                    q -> q.path("foo.bar").query(SearchQueryBuilders.term("foo.bar.field", "val")))
                .toSearchQuery(),
            "Query: {'nested':{'path':'foo.bar','query':{'term':{'foo.bar.field':{'value':'val'}}},'score_mode':'none'}}"));
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
  public void shouldThrowErrorOnNullPath() {
    // given

    // when - throw
    assertThatThrownBy(
            () ->
                SearchQueryBuilders.nested().query(SearchQueryBuilders.term("foo", "bar")).build())
        .hasMessageContaining("Expected a non-null path parameter for the nested query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQuery() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.nested().path("foo.bar").build())
        .hasMessageContaining("Expected a non-null query parameter for the nested query")
        .isInstanceOf(NullPointerException.class);
  }
}
