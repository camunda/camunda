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
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.junit.Test;

public class RepeatingIntervalTest {
  @Test
  public void shouldFailToParseIfStartingWithIntervalDesignator() {
    // given
    final String text = "/P1Y2M3S";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseIfIntervalCannotBeParsed() {
    // given
    final String text = "R/PKF,!T2.:";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseIfNoRepetitionSpecified() {
    // given
    final String text = "PT05S";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseWithoutSpecs() {
    // given
    final String text = "/";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldBeInfiniteIfNoRepetitionsCountSpecified() {
    // given
    final String text = "R/PT05S";
    final RepeatingInterval expected =
        new RepeatingInterval(
            RepeatingInterval.INFINITE, new Interval(Period.ZERO, Duration.ofSeconds(5)));

    // when
    final RepeatingInterval parsed = RepeatingInterval.parse(text);

    // then
    assertThat(parsed).isEqualTo(expected);
  }

  @Test
  public void shouldHaveSpecifiedRepetitionsCount() {
    // given
    final String text = "R5/PT05S";
    final RepeatingInterval expected =
        new RepeatingInterval(5, new Interval(Period.ZERO, Duration.ofSeconds(5)));

    // when
    final RepeatingInterval parsed = RepeatingInterval.parse(text);

    // then
    assertThat(parsed).isEqualTo(expected);
  }

  @Test
  public void shouldFailToParseIfCannotParseRepetitionsCount() {
    // given
    final String text = "RA,/PT05S";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseIfNoInterval() {
    // given
    final String text = "R5/";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseIfRepetitionCountDoesNotStartWithR() {
    // given
    final String text = "B5/PT1S";

    // then
    assertThatThrownBy(() -> RepeatingInterval.parse(text))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldFailToParseEmptyString() {
    assertThatThrownBy(() -> Interval.parse("")).isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldParseWithSpecifiedStartTime() {
    // given
    final String text = "R/2022-05-20T08:09:40+02:00[Europe/Berlin]/PT10S";
    final RepeatingInterval expected =
        new RepeatingInterval(
            -1,
            new Interval(
                Optional.ofNullable(
                    ZonedDateTime.parse("2022-05-20T08:09:40+02:00[Europe/Berlin]")),
                Period.ZERO,
                Duration.ofSeconds(10)));

    // when
    final RepeatingInterval parsed = RepeatingInterval.parse(text);

    // then
    assertThat(parsed).isEqualTo(expected);
  }

  @Test
  public void shouldParseWithSpecialUTCStartTime() {
    // given
    final String text = "R/2022-05-20T08:09:40Z/PT10S";
    final RepeatingInterval expected =
        new RepeatingInterval(
            -1,
            new Interval(
                Optional.ofNullable(ZonedDateTime.parse("2022-05-20T08:09:40Z")),
                Period.ZERO,
                Duration.ofSeconds(10)));

    // when
    final RepeatingInterval parsed = RepeatingInterval.parse(text);

    // then
    assertThat(parsed).isEqualTo(expected);
  }

  @Test
  public void shouldParseWithEmptyStartTime() {
    // given
    final String text = "R//PT10S";
    final RepeatingInterval expected =
        new RepeatingInterval(-1, new Interval(Period.ZERO, Duration.ofSeconds(10)));

    // when
    final RepeatingInterval parsed = RepeatingInterval.parse(text);

    // then
    assertThat(parsed).isEqualTo(expected);
  }
}
