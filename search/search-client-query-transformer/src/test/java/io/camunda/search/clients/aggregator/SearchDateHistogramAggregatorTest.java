/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.search.clients.aggregator.SearchDateHistogramAggregator.DateHistogramInterval.Fixed;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchDateHistogramAggregatorTest {

  // -----------------------------------------------------------------------
  // toExpression — happy path
  // -----------------------------------------------------------------------

  @ParameterizedTest(name = "{0} -> \"{1}\"")
  @CsvSource({
    // milliseconds
    "PT0.001S, 1ms",
    "PT0.500S, 500ms",
    "PT0.999S, 999ms",
    // seconds — exact, no sub-second remainder
    "PT1S,  1s",
    "PT30S, 30s",
    "PT59S, 59s",
    // minutes — exact, no sub-minute remainder
    "PT1M,  1m",
    "PT5M,  5m",
    "PT90M, 90m",
    // hours — exact, no sub-hour remainder
    "PT1H,  1h",
    "PT2H,  2h",
    "PT48H, 48h",
    // days — exact, no sub-day remainder
    "P1D,  1d",
    "P7D,  7d",
    "P30D, 30d",
  })
  void shouldConvertToExpression(final String isoDuration, final String expectedExpression) {
    final var fixed = new Fixed(Duration.parse(isoDuration));
    assertThat(fixed.toExpression()).isEqualTo(expectedExpression.trim());
  }

  @Test
  void shouldUseCoarsestExactUnit() {
    // 60 seconds → minutes wins over seconds
    assertThat(new Fixed(Duration.ofSeconds(60)).toExpression()).isEqualTo("1m");
    // 3600 seconds → hours wins over minutes
    assertThat(new Fixed(Duration.ofSeconds(3600)).toExpression()).isEqualTo("1h");
    // 86400 seconds → days wins over hours
    assertThat(new Fixed(Duration.ofSeconds(86_400)).toExpression()).isEqualTo("1d");
    // 90 minutes — not a whole number of hours, stays as minutes
    assertThat(new Fixed(Duration.ofMinutes(90)).toExpression()).isEqualTo("90m");
    // 25 hours — not a whole number of days, stays as hours
    assertThat(new Fixed(Duration.ofHours(25)).toExpression()).isEqualTo("25h");
    // 1500 ms — not a whole number of seconds, stays as ms
    assertThat(new Fixed(Duration.ofMillis(1500)).toExpression()).isEqualTo("1500ms");
  }

  // -----------------------------------------------------------------------
  // Construction guards
  // -----------------------------------------------------------------------

  @Test
  void shouldRejectNullDuration() {
    assertThatThrownBy(() -> new Fixed(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("duration must not be null");
  }

  @Test
  void shouldRejectZeroDuration() {
    assertThatThrownBy(() -> new Fixed(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duration must be positive");
  }

  @Test
  void shouldRejectNegativeDuration() {
    assertThatThrownBy(() -> new Fixed(Duration.ofMinutes(-5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duration must be positive");
  }

  @Test
  void shouldRejectSubMillisecondDuration() {
    // 1 nanosecond — rejected at construction, not at toExpression()
    assertThatThrownBy(() -> new Fixed(Duration.ofNanos(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sub-millisecond");
    // 1 microsecond
    assertThatThrownBy(() -> new Fixed(Duration.ofNanos(1_000)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sub-millisecond");
    // 1 ms + 1 ns — has sub-millisecond part
    assertThatThrownBy(() -> new Fixed(Duration.ofMillis(1).plusNanos(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sub-millisecond");
  }
}
