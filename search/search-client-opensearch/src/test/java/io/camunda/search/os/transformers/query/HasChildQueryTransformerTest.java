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

public class HasChildQueryTransformerTest {

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
            SearchQueryBuilders.hasChildQuery("fooType", SearchQueryBuilders.term("fooField", 123)),
            "{'has_child':{'query':{'term':{'fooField':{'value':123}}},'score_mode':'none','type':'fooType'}}"),
        Arguments.arguments(
            SearchQueryBuilders.hasChild()
                .type("fooType")
                .query(SearchQueryBuilders.term("fooField", 123))
                .build()
                .toSearchQuery(),
            "{'has_child':{'query':{'term':{'fooField':{'value':123}}},'score_mode':'none','type':'fooType'}}"));
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
  public void shouldThrowErrorOnNullQuery() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.hasChild().type("fooType").build())
        .hasMessageContaining("Expected a non-null query parameter for the hasChild query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullQueryFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.hasChildQuery("fooType", null))
        .hasMessageContaining("Expected a non-null query parameter for the hasChild query.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullType() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchQueryBuilders.hasChild().query(SearchQueryBuilders.term("foo", 1)).build())
        .hasMessageContaining(
            "Expected a non-null type parameter for the hasChild query, with query:")
        .hasMessageContaining("field=foo")
        .hasMessageContaining("value=1")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullTypeFactoryMethod() {
    // given

    // when - throw
    assertThatThrownBy(
            () -> SearchQueryBuilders.hasChildQuery(null, SearchQueryBuilders.term("foo", 1)))
        .hasMessageContaining(
            "Expected a non-null type parameter for the hasChild query, with query:")
        .hasMessageContaining("field=foo")
        .hasMessageContaining("value=1")
        .isInstanceOf(NullPointerException.class);
  }
}
