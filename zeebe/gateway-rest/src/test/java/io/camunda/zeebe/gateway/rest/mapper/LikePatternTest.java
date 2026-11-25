/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Named.named;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class LikePatternTest {

  private static final int UNBOUNDED = Integer.MAX_VALUE;
  private static final String LONGEST = "NOT_YET_ACTIVE";
  private static final String EMOJI = "\uD83D\uDE00";

  @ParameterizedTest(name = "''{0}'' matches ''{1}'': {2}")
  @MethodSource("wildcards")
  void shouldMatchWholeCandidate(final String like, final String candidate, final boolean match) {
    // when
    final var pattern = LikePattern.compile(like, UNBOUNDED);

    // then
    assertThat(pattern)
        .hasValueSatisfying(p -> assertThat(p.matcher(candidate).matches()).isEqualTo(match));
  }

  static Stream<Arguments> wildcards() {
    return Stream.of(
        Arguments.of("ABC", "ABC", true),
        // the whole candidate must match, not a part of it
        Arguments.of("ABC", "ABCD", false),
        Arguments.of("ABC", "XABC", false),
        // * matches any run of characters, including none
        Arguments.of("*", "", true),
        Arguments.of("*", "ABC", true),
        Arguments.of("A*C", "AC", true),
        Arguments.of("A*C", "ABBC", true),
        Arguments.of("A*C", "AB", false),
        Arguments.of("A**C", "ABC", true),
        // ? matches exactly one character
        Arguments.of("A?C", "ABC", true),
        Arguments.of("A?C", "AC", false),
        Arguments.of("*?*", "", false),
        Arguments.of("*?*", "A", true),
        Arguments.of("D*?*D", "DD", false),
        Arguments.of("D*?*D", "DXD", true),
        // the empty value matches only the empty candidate
        Arguments.of("", "", true),
        Arguments.of("", "A", false),
        // matching is case-sensitive
        Arguments.of("abc", "ABC", false),
        // regex syntax has no meaning
        Arguments.of(".", "A", false),
        Arguments.of(".", ".", true),
        Arguments.of("A|B", "A", false),
        Arguments.of("A|B", "A|B", true),
        Arguments.of("[A-Z]", "B", false),
        Arguments.of("[A-Z]", "[A-Z]", true),
        // a backslash makes the next character literal
        Arguments.of("\\*", "*", true),
        Arguments.of("\\*", "A", false),
        Arguments.of("A\\?", "A?", true),
        Arguments.of("A\\?", "AB", false),
        Arguments.of("A\\_B", "A_B", true),
        Arguments.of("\\\\", "\\", true),
        Arguments.of("\\\\E", "\\E", true),
        // a trailing backslash is a literal backslash
        Arguments.of("A\\", "A\\", true),
        Arguments.of("A\\", "A", false),
        // an escaped character splits a run of stars into two wildcards
        Arguments.of("*\\A*", "XAY", true),
        Arguments.of("*\\A*", "XY", false),
        Arguments.of("*\\**", "X*Y", true),
        Arguments.of("*\\**", "XY", false),
        // a character outside the Basic Multilingual Plane is one character, not two
        Arguments.of(EMOJI, EMOJI, true),
        Arguments.of("\\" + EMOJI, EMOJI, true),
        Arguments.of("?", EMOJI, true),
        Arguments.of("??", EMOJI, false));
  }

  @ParameterizedTest(name = "''{0}'' within {1}: {2}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          ???          | 3 | true
          ???          | 2 | false
          ''           | 0 | true
          *            | 0 | true
          *?*?*?*      | 3 | true
          *?*?*?*      | 2 | false
          \\A\\B       | 2 | true
          \\A\\B\\C    | 2 | false
          A\\          | 2 | true
          \uD83D\uDE00 | 1 | true
          """)
  void shouldCountOnlyCharactersAStarCannotMatchAgainstTheBound(
      final String like, final int maxLength, final boolean compiled) {
    // when
    final var pattern = LikePattern.compile(like, maxLength);

    // then stars match nothing on their own, and an escape counts as one character
    assertThat(pattern.isPresent()).isEqualTo(compiled);
  }

  @Test
  void shouldRejectNegativeBound() {
    // when / then
    assertThatThrownBy(() -> LikePattern.compile("*", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxLength must not be negative, was -1");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("hostileValues")
  // a $like value is caller input, so even a hostile one must resolve quickly
  @Timeout(value = 5, threadMode = ThreadMode.SEPARATE_THREAD)
  void shouldResolveHostileValueQuickly(final String like, final boolean matchesLongest) {
    // when
    final var pattern = LikePattern.compile(like, LONGEST.length());

    // then
    assertThat(pattern.map(p -> p.matcher(LONGEST).matches()).orElse(false))
        .isEqualTo(matchesLongest);
  }

  static Stream<Arguments> hostileValues() {
    return Stream.of(
        // a run of stars before a character the candidate lacks backtracks exponentially
        Arguments.of(named("40 '*' then 'Z'", "*".repeat(40) + "Z"), false),
        Arguments.of(named("40 '*' then 'ACTIVE'", "*".repeat(40) + "ACTIVE"), true),
        Arguments.of(named("1M '*'", "*".repeat(1_000_000)), true),
        Arguments.of(named("1M '*' then 15 '?'", "*".repeat(1_000_000) + "?".repeat(15)), false),
        // compiling a long pattern is superlinear, or overflows the stack
        Arguments.of(named("50k '*?' then 'Z'", "*?".repeat(50_000) + "Z"), false),
        Arguments.of(named("500k escaped 'A'", "\\A".repeat(500_000)), false),
        Arguments.of(named("1M 'A'", "A".repeat(1_000_000)), false));
  }
}
