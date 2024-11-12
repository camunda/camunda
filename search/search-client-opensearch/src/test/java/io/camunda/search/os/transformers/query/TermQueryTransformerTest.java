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

public class TermQueryTransformerTest {

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
        Arguments.arguments(SearchQueryBuilders.term("foo", 1), "{'term':{'foo':{'value':1}}}"),
        Arguments.arguments(SearchQueryBuilders.term("foo", 1L), "{'term':{'foo':{'value':1}}}"),
        Arguments.arguments(SearchQueryBuilders.term("foo", 1.0), "{'term':{'foo':{'value':1.0}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term("foo", true), "{'term':{'foo':{'value':true}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term("foo", "string"), "{'term':{'foo':{'value':'string'}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term("foo", (String) null), "{'term':{'foo':{'value':null}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term().field("foo").value((String) null).build().toSearchQuery(),
            "{'term':{'foo':{'value':null}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term()
                .field("foo")
                .value("string")
                .caseInsensitive(true) // if not null it will be set
                .build()
                .toSearchQuery(),
            "{'term':{'foo':{'value':'string','case_insensitive':true}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term()
                .field("foo")
                .value("string")
                .caseInsensitive(false)
                .build()
                .toSearchQuery(),
            "{'term':{'foo':{'value':'string','case_insensitive':false}}}"),
        Arguments.arguments(
            SearchQueryBuilders.term()
                .field("foo")
                .value("string")
                .value(1)
                .value(true) // last one wins
                .build()
                .toSearchQuery(),
            "{'term':{'foo':{'value':true}}}"));
  }

  @ParameterizedTest
  @MethodSource("provideQueries")
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
    assertThatThrownBy(() -> SearchQueryBuilders.term().field("foo").build())
        .hasMessageContaining("Expected non-null value for term query, for field 'foo'.")
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldThrowErrorOnNullField() {
    // given

    // when - throw
    assertThatThrownBy(() -> SearchQueryBuilders.term().build())
        .hasMessageContaining("Expected non-null field for term query.")
        .isInstanceOf(NullPointerException.class);
  }
}
