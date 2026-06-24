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

public class HasParentQueryTransformerTest {

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
            SearchQueryBuilders.hasParentQuery(
                "fooParentType", SearchQueryBuilders.term("fooField", 123)),
            "{\"has_parent\":{\"parent_type\":\"fooParentType\",\"query\":{\"term\":{\"fooField\":{\"value\":123}}}}}"),
        Arguments.arguments(
            SearchQueryBuilders.hasParent()
                .parentType("fooParentType")
                .query(SearchQueryBuilders.term("fooField", 123))
                .build()
                .toSearchQuery(),
            "{\"has_parent\":{\"parent_type\":\"fooParentType\",\"query\":{\"term\":{\"fooField\":{\"value\":123}}}}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueries")
  public void shouldApplyTransformer(final SearchQuery query, final String expectedResultQuery)
      throws IOException {
    // given
    final var expectedQuery = expectedResultQuery.replace("'", "\"");

    // when
    final var result = transformer.apply(query);

    // then
    assertThat(result).isNotNull();
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
  }

  @Test
  public void shouldThrowErrorOnNullQuery() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.hasParent().parentType("fooParentType").build())
        .hasMessageContaining("Expected a non-null query parameter for the hasParent query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQueryFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.hasParentQuery("fooParentType", null))
        .hasMessageContaining("Expected a non-null query parameter for the hasParent query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullParentType() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchQueryBuilders.hasParent().query(SearchQueryBuilders.term("foo", 1)).build())
        .hasMessageContaining(
            "Expected a non-null parentType parameter for the hasParent query, with query:")
        .hasMessageContaining("field=foo")
        .hasMessageContaining("value=1")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullParentTypeFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchQueryBuilders.hasParentQuery(null, SearchQueryBuilders.term("foo", 1)))
        .hasMessageContaining(
            "Expected a non-null parentType parameter for the hasParent query, with query:")
        .hasMessageContaining("field=foo")
        .hasMessageContaining("value=1")
        .isInstanceOf(NullPointerException.class);
  }
}
