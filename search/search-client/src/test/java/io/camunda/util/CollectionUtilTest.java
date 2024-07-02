/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CollectionUtilTest {

  private static Stream<Arguments> provideParams() {
    return Stream.of(
        Arguments.arguments(new ArrayList<>(), List.of(), List.of()),
        Arguments.arguments(new ArrayList<>(), List.of("1"), List.of("1")),
        Arguments.arguments(null, null, List.of()),
        Arguments.arguments(new ArrayList<>(), null, List.of()),
        Arguments.arguments(null, List.of(), List.of()),
        Arguments.arguments(null, List.of("1"), List.of("1")),
        Arguments.arguments(asList("1"), List.of("2"), List.of("1", "2")),
        Arguments.arguments(asList("1"), null, List.of("1")));
  }

  private static List<String> asList(final String... args) {
    return new ArrayList<>(Arrays.stream(args).toList());
  }

  @ParameterizedTest
  @MethodSource("provideParams")
  public void shouldAddValuesToList(
      final List<String> list, final List<String> values, final List<String> expected) {
    // given

    // when
    final var result = CollectionUtil.addValuesToList(list, values);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private static Stream<Arguments> provideArrayParams() {
    return Stream.of(
        Arguments.arguments(null, List.of()),
        Arguments.arguments(new String[0], List.of()),
        Arguments.arguments(new String[] {"1", "2"}, List.of("1", "2")));
  }

  @ParameterizedTest
  @MethodSource("provideArrayParams")
  public void shouldCollectValuesAsList(final String[] values, final List<String> expected) {
    // given

    // when
    final var result = CollectionUtil.collectValuesAsList(values);

    // then
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void shouldRemoveNullValuesFromStringArray() {
    // given
    final String[] values = new String[] {"foo", null, "bar"};

    // when
    final var result = CollectionUtil.withoutNull(values);

    // then
    assertThat(result).hasSize(2);
  }

  @Test
  public void shouldRemoveNullValuesFromCollection() {
    // given
    final var values = new ArrayList<String>();
    values.add("foo");
    values.add(null);
    values.add("bar");

    // when
    final var result = CollectionUtil.withoutNull(values);

    // then
    assertThat(result).hasSize(2);
  }
}
