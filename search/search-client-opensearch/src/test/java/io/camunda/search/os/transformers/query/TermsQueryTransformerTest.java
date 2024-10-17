/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import io.camunda.search.os.util.OSQuerySerializer;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class TermsQueryTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchQuery, Query> transformer;

  private OSQuerySerializer osQuerySerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchQuery.class);

    // To serialize OS queries to json
    osQuerySerializer = new OSQuerySerializer();
  }

  @AfterEach
  public void close() throws Exception {
    osQuerySerializer.close();
  }

  private static Stream<Arguments> provideRangeQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.intTerms("foo", List.of(1, 2)), "{'terms':{'foo':[1,2]}}"),
        Arguments.arguments(
            SearchQueryBuilders.longTerms("foo", List.of(1L, 2L)), "{'terms':{'foo':[1,2]}}"),
        Arguments.arguments(
            SearchQueryBuilders.stringTerms("foo", List.of("ele1", "ele2")),
            "{'terms':{'foo':['ele1','ele2']}}"),
        Arguments.arguments(
            SearchQueryBuilders.terms()
                .field("foo")
                .stringTerms(Arrays.stream(new String[] {"ele1", null}).toList())
                .build()
                .toSearchQuery(),
            "{'terms':{'foo':['ele1',null]}}"),
        Arguments.arguments(
            SearchQueryBuilders.terms()
                .field("foo")
                .stringTerms(List.of("ele1", "ele2"))
                .build()
                .toSearchQuery(),
            "{'terms':{'foo':['ele1','ele2']}}"));
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
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
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
