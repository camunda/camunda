/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

final class CaseTitleTest {

  private static final String TITLE_VARIABLE = "loanTitle";
  private static final String FALLBACK = "Loan application";
  private static final DirectBuffer TITLE_VARIABLE_BUFFER = BufferUtil.wrapString(TITLE_VARIABLE);

  @Test
  void shouldUseStringTitle() {
    // given
    final var variables = asMsgPack(TITLE_VARIABLE, "Loan for ACME");

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo("Loan for ACME");
  }

  @Test
  void shouldUseNumberAsTitle() {
    // given
    final var variables = asMsgPack(TITLE_VARIABLE, 12345);

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo("12345");
  }

  @Test
  void shouldUseBooleanAsTitle() {
    // given
    final var variables = asMsgPack(TITLE_VARIABLE, true);

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo("true");
  }

  @ParameterizedTest
  @MethodSource("unusableTitles")
  void shouldUseFallback(final DirectBuffer variables) {
    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo(FALLBACK);
  }

  private static Stream<Arguments> unusableTitles() {
    final Map<String, Object> nullTitle = new HashMap<>();
    nullTitle.put(TITLE_VARIABLE, null);
    return Stream.of(
        Arguments.argumentSet("variable is missing", asMsgPack("unrelated", "value")),
        Arguments.argumentSet("variable is null", asMsgPack(nullTitle)),
        Arguments.argumentSet("variable is empty", asMsgPack(TITLE_VARIABLE, "")),
        Arguments.argumentSet("variable is blank", asMsgPack(TITLE_VARIABLE, "   ")),
        Arguments.argumentSet(
            "variable is an object", asMsgPack(TITLE_VARIABLE, Map.of("name", "my case"))),
        Arguments.argumentSet(
            "variable is an array", asMsgPack(TITLE_VARIABLE, List.of("my case"))));
  }

  @Test
  void shouldUseFallbackForEmptyDocument() {
    // given
    final DirectBuffer variables = new UnsafeBuffer(new byte[0]);

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo(FALLBACK);
  }

  @Test
  void shouldTruncateLongTitle() {
    // given
    final var variables = asMsgPack(TITLE_VARIABLE, "x".repeat(CaseTitle.MAX_LENGTH + 44));

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo("x".repeat(CaseTitle.MAX_LENGTH));
  }

  @Test
  void shouldKeepTitleOfExactlyMaxLength() {
    // given
    final var expected = "x".repeat(CaseTitle.MAX_LENGTH);
    final var variables = asMsgPack(TITLE_VARIABLE, expected);

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> FALLBACK);

    // then
    assertThat(title).isEqualTo(expected);
  }

  @Test
  void shouldTruncateLongFallback() {
    // given
    final var variables = asMsgPack("unrelated", "value");
    final var longFallback = "y".repeat(CaseTitle.MAX_LENGTH + 44);

    // when
    final var title = CaseTitle.of(TITLE_VARIABLE_BUFFER, variables, () -> longFallback);

    // then
    assertThat(title).isEqualTo("y".repeat(CaseTitle.MAX_LENGTH));
  }

  @Test
  void shouldNotCallFallbackWhenTitleIsFound() {
    // given
    final var variables = asMsgPack(TITLE_VARIABLE, "Loan for ACME");
    final var fallbackCalls = new AtomicInteger();

    // when
    CaseTitle.of(
        TITLE_VARIABLE_BUFFER,
        variables,
        () -> {
          fallbackCalls.incrementAndGet();
          return FALLBACK;
        });

    // then
    assertThat(fallbackCalls).hasValue(0);
  }
}
