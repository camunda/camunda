/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.query.SearchRangeQuery;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class RangeQueryTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchRangeQuery, RangeQuery> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchRangeQuery.class);
  }

  private static Stream<Arguments> provideRangeQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(null).build(),
            "Query: {'range':{'foo':{}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(Long.MAX_VALUE).build(),
            "Query: {'range':{'foo':{'lt':9223372036854775807}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(Long.MIN_VALUE).build(),
            "Query: {'range':{'foo':{'lt':-9223372036854775808}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(123456789L).build(),
            "Query: {'range':{'foo':{'lt':123456789}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").gte(123456L).build(),
            "Query: {'range':{'foo':{'gte':123456}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lte(12345L).build(),
            "Query: {'range':{'foo':{'lte':12345}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").gt(1234L).build(),
            "Query: {'range':{'foo':{'gt':1234}}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").format("format").build(),
            "Query: {'range':{'foo':{'format':'format'}}}"));
  }

  @ParameterizedTest
  @MethodSource("provideRangeQueries")
  public void shouldApplyTransformer(
      final SearchRangeQuery rangeQuery, final String expectedResultQuery) {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(rangeQuery);

    // then
    assertThat(result).isNotNull();
    assertThat(result._toQuery()).isNotNull();
    assertThat(result._toQuery().toString()).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldCombineMultipleRangeQueries() {
    // given
    final SearchRangeQuery query =
        SearchQueryBuilders.range()
            .field("foo")
            .lt(123456789L)
            .lte(12345L)
            .gte(123456L)
            .gt(1234L)
            .build();

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isNotNull();
    assertThat(result.untyped().lt().to(Long.class)).isEqualTo(123456789L);
    assertThat(result.untyped().gt().to(Long.class)).isEqualTo(1234L);
    assertThat(result.untyped().gte().to(Long.class)).isEqualTo(123456L);
    assertThat(result.untyped().lte().to(Long.class)).isEqualTo(12345L);
  }
}
