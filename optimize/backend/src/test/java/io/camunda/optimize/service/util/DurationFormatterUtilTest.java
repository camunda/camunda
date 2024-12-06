/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

public class DurationFormatterUtilTest {

  @Test
  void shouldReturnReadableStringForDurationInMilliseconds() {
    // given
    final long durationInMs = 4000000000L; // 46 days, 6 hours, 40 minutes

    // when
    final String result =
        DurationFormatterUtil.formatMilliSecondsToReadableDurationString(durationInMs);

    // then
    assertThat(result).isEqualTo("1mo 2wks 2d 7h 6min 40s");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1000})
  void shouldReturnDashForZeroOrNegativeDuration(final int durationInMs) {
    // when
    String result = DurationFormatterUtil.formatMilliSecondsToReadableDurationString(durationInMs);
    // then
    assertThat(result).isEqualTo("-");
  }

  @Test
  void shouldFormatWithMillisecondsRemaining() {
    // given
    final long durationInMs = 1234567; // 20 minutes, 34 seconds, 567 milliseconds

    // when
    final String result =
        DurationFormatterUtil.formatMilliSecondsToReadableDurationString(durationInMs);

    // then
    assertThat(result).isEqualTo("20min 34s 567ms");
  }

  @Test
  void shouldHandleLargeDurationsIncludingYearsAndMonths() {
    // given
    final long durationInMs =
        365L * 24 * 60 * 60 * 1000
            + // 1 year
            30L * 24 * 60 * 60 * 1000
            + // 1 month
            7L * 24 * 60 * 60 * 1000
            + // 1 week
            24 * 60 * 60 * 1000; // 1 day

    // when
    final String result =
        DurationFormatterUtil.formatMilliSecondsToReadableDurationString(durationInMs);

    // then
    assertThat(result).isEqualTo("1yrs 1mo 1wks 1d");
  }

  @ParameterizedTest
  @CsvSource({"15,15min", "0,''"})
  void shouldFormatDurationStringCorrectly(final int duration, final String expected) {
    // given
    final String unitString = "min";

    // when
    String result = DurationFormatterUtil.formatDuration(unitString, duration);

    // then
    assertThat(result).isEqualTo(expected);
  }
}
