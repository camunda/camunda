/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.query.SearchTermQuery;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.filter.UserFilter.Builder;
import io.camunda.util.ObjectBuilder;
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

  public static Stream<Arguments> queryFilterParameters() {
    return Stream.of(
        Arguments.of((Function<Builder, ObjectBuilder<UserFilter>>) f -> f.key(1L), "key", 1L),
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
}
