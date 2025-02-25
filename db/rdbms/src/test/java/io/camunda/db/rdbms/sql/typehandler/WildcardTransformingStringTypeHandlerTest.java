/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.typehandler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WildcardTransformingStringTypeHandlerTest {

  @ParameterizedTest
  @MethodSource("provideElasticsearchQueries")
  void shouldTransformESWildcardToSQLWildcard(final String input, final String expected) {
    final String result = WildcardTransformingStringTypeHandler.transformParameter(input);
    assertEquals(expected, result);
  }

  private static Stream<Arguments> provideElasticsearchQueries() {
    return Stream.of(
        Arguments.of("some*query", "some%query"),
        Arguments.of("some\\*query", "some*query"),
        Arguments.of("some?query", "some_query"),
        Arguments.of("some\\?query", "some?query"),
        Arguments.of("some*query?with*wildcards", "some%query_with%wildcards"),
        Arguments.of("some\\*query\\?with*wildcards?", "some*query?with%wildcards_"));
  }

  private static Stream<Arguments> provideSqlQueries() {
    return Stream.of(
        Arguments.of("some%query", "some\\%query"),
        Arguments.of("some\\%query", "some\\%query"),
        Arguments.of("some_query", "some\\_query"),
        Arguments.of("some\\_query", "some\\_query"),
        Arguments.of("some%query_with%wildcards", "some\\%query\\_with\\%wildcards"),
        Arguments.of("some\\*query\\_with%wild*car\\?ds?", "some*query\\_with\\%wild%car?ds_"));
  }

  @ParameterizedTest
  @MethodSource("provideSqlQueries")
  void shouldEscapeSqlWildcards(final String input, final String expected) {
    final String result = WildcardTransformingStringTypeHandler.transformParameter(input);
    assertEquals(expected, result);
  }
}
