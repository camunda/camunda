/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumberParsingUtilTest {

  @Test
  void shouldReturnEmptyListForEmptyInput() {
    // when
    final var result = NumberParsingUtil.parseLongs(List.of());

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyListForNullInput() {
    // when
    final var result = NumberParsingUtil.parseLongs(null);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  void shouldParseNumericStringsToLongs() {
    // given
    final var input = List.of("1", "-5", "007", "42", "9999999999");

    // when
    final var result = NumberParsingUtil.parseLongs(input);

    // then
    assertThat(result)
        .as("Expects all numeric strings to be parsed to Long values")
        .hasSameSizeAs(input)
        .containsExactly(1L, -5L, 7L, 42L, 9_999_999_999L);
  }

  @Test
  void shouldIgnoreNonNumericValues() {
    // given
    final var input = Arrays.asList("foo", null, "", "bar123", "--5");

    // when
    final var result = NumberParsingUtil.parseLongs(input);

    // then
    assertThat(result).as("Expects all non-numeric values to be ignored").isEmpty();
  }
}
