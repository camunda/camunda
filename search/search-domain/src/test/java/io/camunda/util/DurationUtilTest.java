/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class DurationUtilTest {

  // -----------------------------------------------------------------------
  // min / max
  // -----------------------------------------------------------------------

  @Test
  void minReturnsFirstWhenSmaller() {
    assertThat(DurationUtil.min(Duration.ofSeconds(30), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void minReturnsSecondWhenSmaller() {
    assertThat(DurationUtil.min(Duration.ofMinutes(2), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void minReturnsEitherWhenEqual() {
    final var duration = Duration.ofMinutes(1);
    assertThat(DurationUtil.min(duration, duration)).isEqualTo(duration);
  }

  @Test
  void maxReturnsFirstWhenLarger() {
    assertThat(DurationUtil.max(Duration.ofMinutes(2), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void maxReturnsSecondWhenLarger() {
    assertThat(DurationUtil.max(Duration.ofSeconds(30), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void maxReturnsEitherWhenEqual() {
    final var duration = Duration.ofMinutes(1);
    assertThat(DurationUtil.max(duration, duration)).isEqualTo(duration);
  }

  // -----------------------------------------------------------------------
  // toEsOsInterval — parameterised happy path
  // -----------------------------------------------------------------------

  @ParameterizedTest(name = "{0} -> \"{1}\"")
  @CsvSource({
    // nanoseconds (exact — no sub-millisecond remainder)
    "PT0.000000001S, 0ms",
    "PT0.000000500S, 0ms",
    // milliseconds
    "PT0.001S, 1ms",
    "PT0.500S, 500ms",
    "PT0.999S, 999ms",
    // seconds
    "PT1S,  1s",
    "PT30S, 30s",
    "PT59S, 59s",
    // minutes (exact — no sub-minute remainder)
    "PT1M,  1m",
    "PT5M,  5m",
    "PT90M, 90m",
    // hours (exact — no sub-hour remainder)
    "PT1H,  1h",
    "PT2H,  2h",
    "PT48H, 2d",
    // days (exact — no sub-day remainder)
    "P1D,   1d",
    // weeks (exact multiple of 7 days)
    "P7D,   7d",
    "P14D,  14d"
  })
  void shouldConvertToEsOsInterval(final String isoDuration, final String expectedExpression) {
    assertThat(DurationUtil.toEsOsInterval(Duration.parse(isoDuration)))
        .isEqualTo(expectedExpression.trim());
  }

  // -----------------------------------------------------------------------
  // toEsOsInterval — coarsest-unit logic
  // -----------------------------------------------------------------------

  @Test
  void shouldPickCoarsestExactUnit() {
    // 60 s → minutes, not seconds
    assertThat(DurationUtil.toEsOsInterval(Duration.ofSeconds(60))).isEqualTo("1m");
    // 3600 s → hours, not minutes
    assertThat(DurationUtil.toEsOsInterval(Duration.ofSeconds(3_600))).isEqualTo("1h");
    // 86400 s → days, not hours
    assertThat(DurationUtil.toEsOsInterval(Duration.ofSeconds(86_400))).isEqualTo("1d");
    // 7 days → days as this is the largest unit that divides it exactly; not weeks as weeks are not
    // a built-in unit in ES/OS intervals
    assertThat(DurationUtil.toEsOsInterval(Duration.ofDays(7))).isEqualTo("7d");
    // 1000 ms = 1 s → expressed as seconds, not milliseconds
    assertThat(DurationUtil.toEsOsInterval(Duration.ofMillis(1_000))).isEqualTo("1s");
  }

  @Test
  void shouldNotCoarsenWhenNotExactMultiple() {
    // 90 minutes — not a whole number of hours
    assertThat(DurationUtil.toEsOsInterval(Duration.ofMinutes(90))).isEqualTo("90m");
    // 25 hours — not a whole number of days
    assertThat(DurationUtil.toEsOsInterval(Duration.ofHours(25))).isEqualTo("25h");
    // 10 days — not a whole number of weeks
    assertThat(DurationUtil.toEsOsInterval(Duration.ofDays(10))).isEqualTo("10d");
    // 1500 ms — not a whole number of seconds
    assertThat(DurationUtil.toEsOsInterval(Duration.ofMillis(1_500))).isEqualTo("1500ms");
  }

  // -----------------------------------------------------------------------
  // toEsOsInterval — error cases
  // -----------------------------------------------------------------------

  @Test
  void shouldThrowForZeroOrNegativeDuration() {
    assertThatThrownBy(() -> DurationUtil.toEsOsInterval(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than zero");
    assertThatThrownBy(() -> DurationUtil.toEsOsInterval(Duration.ofMinutes(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("greater than zero");
  }
}
