/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.query.SearchRangeQuery;
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
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;

public class RangeQueryTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchRangeQuery, RangeQuery> transformer;

  private OSQuerySerializer osQuerySerializer;

  @BeforeEach
  public void before() throws IOException {
    transformer = transformers.getTransformer(SearchRangeQuery.class);

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
            SearchQueryBuilders.range().field("foo").lt(null).build(), "{'foo':{}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(Long.MAX_VALUE).build(),
            "{'foo':{'lt':9223372036854775807}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(Long.MIN_VALUE).build(),
            "{'foo':{'lt':-9223372036854775808}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lt(123456789L).build(),
            "{'foo':{'lt':123456789}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").gte(123456L).build(),
            "{'foo':{'gte':123456}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").lte(12345L).build(), "{'foo':{'lte':12345}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").gt(1234L).build(), "{'foo':{'gt':1234}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").format("format").build(),
            "{'foo':{'format':'format'}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").to("toBar").build(), "{'foo':{'to':'toBar'}}"),
        Arguments.arguments(
            SearchQueryBuilders.range().field("foo").from("fromBar").build(),
            "{'foo':{'from':'fromBar'}}"));
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
    Assertions.assertThat(osQuerySerializer.serialize(result)).isEqualTo(expectedQuery);
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
    assertThat(result.lt().to(Long.class)).isEqualTo(123456789L);
    assertThat(result.gt().to(Long.class)).isEqualTo(1234L);
    assertThat(result.gte().to(Long.class)).isEqualTo(123456L);
    assertThat(result.lte().to(Long.class)).isEqualTo(12345L);
  }
}
