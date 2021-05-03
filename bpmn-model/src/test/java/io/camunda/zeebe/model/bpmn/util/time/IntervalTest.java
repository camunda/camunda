/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.model.bpmn.util.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class IntervalTest {
  @Test
  public void shouldParseDurationWithNoPeriod() {
    // given
    final String text = "PT05S";
    final Interval expected = new Interval(Period.ZERO, Duration.ofSeconds(5));

    // when
    final Interval interval = Interval.parse(text);

    // then
    assertThat(interval).isEqualTo(expected);
  }

  @Test
  public void shouldParsePeriodWithNoDuration() {
    // given
    final String text = "P1Y2M4D";
    final Interval expected = new Interval(Period.of(1, 2, 4), Duration.ZERO);

    // when
    final Interval interval = Interval.parse(text);

    // then
    assertThat(interval).isEqualTo(expected);
  }

  @Test
  public void shouldParseWithPeriodAndDuration() {
    // given
    final String text = "P1Y2M4DT1H2M3S";
    final Interval expected =
        new Interval(
            Period.of(1, 2, 4),
            Duration.ofHours(1).plus(Duration.ofMinutes(2)).plus(Duration.ofSeconds(3)));

    // when
    final Interval interval = Interval.parse(text);

    // then
    assertThat(interval).isEqualTo(expected);
  }

  @Test
  public void shouldParseNegativeInterval() {
    // given
    final String text = "-P1Y2M4DT1H2M3S";
    final Interval expected =
        new Interval(
            Period.of(-1, -2, -4),
            Duration.ofHours(-1).plus(Duration.ofMinutes(-2)).plus(Duration.ofSeconds(-3)));

    // when
    final Interval interval = Interval.parse(text);

    // then
    assertThat(interval).isEqualTo(expected);
  }

  @Test
  public void shouldParsePositiveInterval() {
    // given
    final String text = "+P1Y2M4DT1H2M3S";
    final Interval expected =
        new Interval(
            Period.of(1, 2, 4),
            Duration.ofHours(1).plus(Duration.ofMinutes(2)).plus(Duration.ofSeconds(3)));

    // when
    final Interval interval = Interval.parse(text);

    // then
    assertThat(interval).isEqualTo(expected);
  }

  @Test
  public void shouldFailToParseWrongPeriod() {
    // given
    final String text = "P,DT1H2M3S";

    // then
    assertThatThrownBy(() -> Interval.parse(text)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseWrongDuration() {
    // given
    final String text = "P1Y2D3MDT!";

    // then
    assertThatThrownBy(() -> Interval.parse(text)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseWrongPeriodAndDuration() {
    // given
    final String text = "PGKLDT4.?";

    // then
    assertThatThrownBy(() -> Interval.parse(text)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseIfNotStartingWithP() {
    // given
    final String text = "T01S";

    // then
    assertThatThrownBy(() -> Interval.parse(text)).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldGetDurationTemporalUnit() {
    // given
    final Interval interval = new Interval(Period.of(1, 2, 3), Duration.ofSeconds(5, 35));
    final ChronoUnit[] durationUnits = new ChronoUnit[] {ChronoUnit.SECONDS, ChronoUnit.NANOS};

    // then
    for (final ChronoUnit unit : durationUnits) {
      assertThat(interval.get(unit)).isEqualTo(interval.getDuration().get(unit));
    }
  }

  @Test
  public void shouldGetPeriodTemporalUnit() {
    // given
    final Interval interval = new Interval(Period.of(1, 2, 3), Duration.ofSeconds(5, 35));
    final ChronoUnit[] periodUnits =
        new ChronoUnit[] {ChronoUnit.DAYS, ChronoUnit.MONTHS, ChronoUnit.YEARS};

    // then
    for (final ChronoUnit unit : periodUnits) {
      assertThat(interval.get(unit)).isEqualTo(interval.getPeriod().get(unit));
    }
  }

  @Test
  public void shouldThrowExceptionOnGetUnsupportedTemporalUnit() {
    // given
    final Interval interval = new Interval(Period.of(1, 2, 3), Duration.ofSeconds(5, 35));
    final List<ChronoUnit> supportedUnits =
        Arrays.asList(
            ChronoUnit.SECONDS,
            ChronoUnit.NANOS,
            ChronoUnit.DAYS,
            ChronoUnit.MONTHS,
            ChronoUnit.YEARS);
    final List<ChronoUnit> unsupportedUnits =
        Arrays.stream(ChronoUnit.values())
            .filter(unit -> !supportedUnits.contains(unit))
            .collect(Collectors.toList());

    // then
    for (final ChronoUnit unit : unsupportedUnits) {
      assertThatThrownBy(() -> interval.get(unit))
          .isInstanceOf(UnsupportedTemporalTypeException.class);
    }
  }

  @Test
  public void shouldReturnAllPeriodAndDurationUnits() {
    // given
    final Interval interval = new Interval(Period.of(1, 2, 3), Duration.ofSeconds(5, 35));

    // then
    assertThat(interval.getUnits())
        .containsExactlyInAnyOrder(
            ChronoUnit.SECONDS,
            ChronoUnit.NANOS,
            ChronoUnit.MONTHS,
            ChronoUnit.DAYS,
            ChronoUnit.YEARS);
  }

  @Test
  public void shouldAddToTemporalAmount() {
    // given
    final Period period = Period.of(1, 2, 3);
    final Duration duration = Duration.ofSeconds(5, 35);
    final Interval interval = new Interval(period, duration);
    final LocalDateTime amount = LocalDateTime.parse("2007-12-03T10:15:30");
    final LocalDateTime expected = amount.plus(period).plus(duration);

    // then
    assertThat(interval.addTo(amount)).isEqualTo(expected);
  }

  @Test
  public void shouldFailToParseEmptyString() {
    assertThatThrownBy(() -> Interval.parse("")).isInstanceOf(DateTimeParseException.class);
  }
}
