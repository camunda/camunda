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
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch._types.query_dsl.Query;

public class ConstantScoreQueryTransformerTest {

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

  private static Stream<Arguments> provideQueries() {
    return Stream.of(
        Arguments.arguments(
            SearchQueryBuilders.constantScore(SearchQueryBuilders.exists("foo")),
            "{'constant_score':{'filter':{'exists':{'field':'foo'}}}}"),
        Arguments.arguments(
            SearchQueryBuilders.constantScore()
                .filter(SearchQueryBuilders.exists("foo"))
                .build()
                .toSearchQuery(),
            "{'constant_score':{'filter':{'exists':{'field':'foo'}}}}"));
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
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldThrowErrorOnNullFilter() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.constantScore().build())
        .hasMessageContaining("Expected a non-null filter for the constant score query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullFieldFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.constantScore((SearchQuery) null))
        .hasMessageContaining("Expected a non-null filter for the constant score query.")
        .isInstanceOf(NullPointerException.class);
  }
}
