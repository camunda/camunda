/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public class AggregationNameUtilTest {

  @ParameterizedTest
  @ValueSource(strings = {"var[]>Name", "[varName", "varName]", ">>>varName"})
  public void shouldDetermineExistenceOfIllegalCharacter(final String input) {
    // when
    final boolean containsIllegalChar = AggregationNameUtil.containsIllegalChar(input);

    // then
    assertThat(containsIllegalChar).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"varName", "_varName_", "!@$%^*&O*", "\\\\!@#"})
  public void shouldNotDetermineExistenceOfIllegalCharacterInValidString(final String input) {
    // when
    final boolean containsIllegalChar = AggregationNameUtil.containsIllegalChar(input);

    // then
    assertThat(containsIllegalChar).isFalse();
  }

  @ParameterizedTest
  @MethodSource("namesAndExpectedOutput")
  public void shouldSanitiseAggregationNamesToStripIllegalCharacters(
      final String input, final String expectedOutput) {
    // when
    final String sanitisedAggregationName = AggregationNameUtil.sanitiseAggName(input);

    // then
    assertThat(sanitisedAggregationName).isEqualTo(expectedOutput);
  }

  private static Stream<Arguments> namesAndExpectedOutput() {
    return Stream.of(
        Arguments.of("varName", "varName"),
        Arguments.of("varName>[]", "varName___"),
        Arguments.of("_var[[[Name_", "_var___Name_"),
        Arguments.of("[varName]", "_varName_"),
        Arguments.of(">[]>[]>[]", "_________"));
  }
}
