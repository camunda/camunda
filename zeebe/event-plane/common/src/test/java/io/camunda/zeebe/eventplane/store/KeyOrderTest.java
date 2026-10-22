/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class KeyOrderTest {

  @Test
  void shouldOrderByTheBytesOfTheUtf8Encoding() {
    // given a character outside the basic multilingual plane, which UTF-16 orders before U+FF5E
    final var supplementary = "a😀";
    final var fullwidth = "a～";

    // when, then
    assertThat(supplementary.compareTo(fullwidth)).isNegative();
    assertThat(KeyOrder.compare(supplementary, fullwidth)).isPositive();
    assertThat(compareBytes(supplementary, fullwidth)).isPositive();
  }

  @Test
  void shouldOrderAPrefixBeforeWhatExtendsIt() {
    // when, then
    assertThat(KeyOrder.compare("a", "a/b")).isNegative();
    assertThat(KeyOrder.compare("a/b", "a")).isPositive();
    assertThat(KeyOrder.compare("a/b", "a/b")).isZero();
    assertThat(KeyOrder.compare("", "a")).isNegative();
  }

  @Test
  void shouldSortSlashAfterDashAndDotAndBeforeDigits() {
    // given
    final var keys = new ArrayList<>(List.of("a0", "a/", "a.", "a-", "a"));

    // when
    keys.sort(KeyOrder.COMPARATOR);

    // then
    assertThat(keys).containsExactly("a", "a-", "a.", "a/", "a0");
  }

  @Test
  void shouldAgreeWithTheBytesOfTheEncodingForMixedText() {
    // given
    final var names = List.of("ü", "u", "z", "あ", "😀", "~", "", "%C3%BC", "ab", "aB");

    // when, then
    for (final var a : names) {
      for (final var b : names) {
        assertThat(Integer.signum(KeyOrder.compare(a, b)))
            .as("%s against %s", a, b)
            .isEqualTo(Integer.signum(compareBytes(a, b)));
      }
    }
  }

  private static int compareBytes(final String a, final String b) {
    return Arrays.compareUnsigned(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
