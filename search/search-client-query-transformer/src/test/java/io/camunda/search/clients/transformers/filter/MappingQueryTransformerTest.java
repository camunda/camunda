/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.clients.query.SearchTermsQuery;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.filter.MappingFilter.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MappingQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<MappingFilter>> fn,
      final String column,
      final Object value) {
    // given
    final var filter = FilterBuilders.mapping(fn);

    // when
    final var searchRequest = transformQuery(filter);

    // then
    final var queryVariant = searchRequest.queryOption();

    if (value instanceof final List<?> values) {
      assertListValues(column, queryVariant, values);
    } else {
      assertThat(queryVariant)
          .isInstanceOfSatisfying(
              SearchTermQuery.class,
              t -> {
                assertThat(t.field()).isEqualTo(column);
                assertThat(t.value().value()).isEqualTo(value);
              });
    }
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>)
                f -> f.claimNames(List.of("foo", "bar")),
            "claimName",
            List.of("foo", "bar")),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>) f -> f.claimName("barfoo"),
            "claimName",
            "barfoo"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<MappingFilter>>) f -> f.claimValue("foobar"),
            "claimValue",
            "foobar"));
  }

  private void assertListValues(
      final String column, final SearchQueryOption queryVariant, final List<?> values) {
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermsQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(column);
              assertThat(t.values().stream().map(TypedValue::stringValue).toList())
                  .isEqualTo(values);
            });
  }
}
