/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchBoolQuery;
import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserFilter.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UserQueryTransformerTest extends AbstractTransformerTest {

  @ParameterizedTest
  @MethodSource("queryFilterParameters")
  public void shouldQueryByField(
      final Function<Builder, ObjectBuilder<UserFilter>> fn,
      final String column,
      final Object value) {
    // given
    final var userFilter = FilterBuilders.user(fn);

    // when
    final var searchRequest = transformQuery(userFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchTermQuery.class,
            t -> {
              assertThat(t.field()).isEqualTo(column);
              assertThat(t.value().value()).isEqualTo(value);
            });
  }

  @ParameterizedTest
  @MethodSource("queryFilterParametersForListOfKeys")
  public void shouldQueryByListOfKeys(
      final Function<Builder, ObjectBuilder<UserFilter>> fn, final Set<Long> keys) {
    // given
    final var userFilter = FilterBuilders.user(fn);

    // when
    final var searchRequest = transformQuery(userFilter);

    // then
    final var queryVariant = searchRequest.queryOption();
    assertThat(queryVariant)
        .isInstanceOfSatisfying(
            SearchBoolQuery.class, // Ensure it's a BoolQuery wrapping multiple terms
            t -> {
              // Assert the number of queries matches the number of keys
              assertThat(t.should()).hasSize(keys.size());

              // Assert all should-clause elements are TermQueries targeting the "key" field
              assertThat(t.should())
                  .allSatisfy(
                      query ->
                          assertThat(query.queryOption())
                              .isInstanceOfSatisfying(
                                  SearchTermQuery.class,
                                  term -> {
                                    assertThat(term.field()).isEqualTo("key");
                                    assertThat(term.value().value()).isIn(keys);
                                  }));
            });
  }

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of((Function<Builder, ObjectBuilder<UserFilter>>) f -> f.key(1L), "key", 1L),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.keys(Set.of(1L)), "key", 1L),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.username("username1"),
            "username",
            "username1"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.name("name1"), "name", "name1"),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.email("email1"),
            "email",
            "email1"));
  }

  public static Stream<Arguments> queryFilterParametersForListOfKeys() {
    return Stream.of(
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.keys(Set.of(1L, 2L)),
            Set.of(1L, 2L)),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.keys(Set.of(3L, 4L, 5L)),
            Set.of(3L, 4L, 5L)),
        Arguments.of(
            (Function<Builder, ObjectBuilder<UserFilter>>) f -> f.keys(Set.of(3L, 4L, 5L, 6L)),
            Set.of(3L, 4L, 5L, 6L)));
  }
}
