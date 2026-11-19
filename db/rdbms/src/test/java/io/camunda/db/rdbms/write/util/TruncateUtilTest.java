/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TruncateUtilTest {

  @Test
  void shouldKeepValueWithinLimits() {
    // when
    final var truncated = TruncateUtil.truncateValue("äbc", 10, 10);

    // then
    assertThat(truncated).isEqualTo("äbc");
  }

  @Test
  void shouldTruncateToCharacterLimit() {
    // when
    final var truncated = TruncateUtil.truncateValue("abcdef", 3, null);

    // then
    assertThat(truncated).isEqualTo("abc");
  }

  @ParameterizedTest
  @CsvSource({
    // 2-byte characters: 3 fit into 7 bytes
    "äöüß, 7, äöü",
    // 3-byte characters: 2 fit into 8 bytes
    "日本語テキスト, 8, 日本",
    // mixed: a (1) + ä (2) + 日 (3) = 6 bytes, the next 日 does not fit
    "aä日日, 8, aä日",
    // limit falls exactly on a character boundary
    "日本語, 6, 日本",
    // not even the first character fits
    "日本語, 2, ''"
  })
  void shouldTruncateToLongestPrefixThatFitsTheByteLimit(
      final String value, final int maxBytes, final String expected) {
    // when
    final var truncated = TruncateUtil.truncateBytes(value, maxBytes);

    // then
    assertThat(truncated).isEqualTo(expected);
    assertThat(truncated.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(maxBytes);
  }

  @Test
  void shouldNotSplitSurrogatePair() {
    // given - each emoji is a surrogate pair in Java and 4 bytes in UTF-8
    final var value = "😀😀";

    // when - 6 bytes fit the first emoji but only half of the second
    final var truncated = TruncateUtil.truncateBytes(value, 6);

    // then
    assertThat(truncated).isEqualTo("😀");
  }

  @Test
  void shouldTruncateLongMultiByteValueToTheByteLimit() {
    // given - 4000 3-byte characters, as Oracle's 4000 character and byte limit would see them
    final var value = "日".repeat(4000);

    // when
    final var truncated = TruncateUtil.truncateValue(value, 4000, 4000);

    // then
    assertThat(truncated).isEqualTo("日".repeat(1333));
  }
}
