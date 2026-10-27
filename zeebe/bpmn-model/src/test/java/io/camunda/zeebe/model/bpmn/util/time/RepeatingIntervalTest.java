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
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
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

  @Test
  public void shouldCalculateDueDate() {
    // given
    final Interval interval = new Interval(Period.ZERO, Duration.ofSeconds(10));
    final long dueDate = interval.toEpochMilli(System.currentTimeMillis());
    final long expected = dueDate + 10_000L;

    // when
    final long newDueDate =
        interval.withStart(Instant.ofEpochMilli(dueDate)).toEpochMilli(System.currentTimeMillis());

    // then
    assertThat(newDueDate).isEqualTo(expected);
  }

  @Test
  public void shouldReturnFromEpochMilliWhenStartDateIsInThePast() {
    // given
    final Instant currentTime = Instant.now();
    final long fromEpochMilli = currentTime.toEpochMilli();
    final Instant pastStart = currentTime.minus(Duration.ofDays(1));
    final Interval interval =
        new Interval(
            Optional.of(ZonedDateTime.ofInstant(pastStart, ZoneId.systemDefault())),
            Period.ZERO,
            Duration.ofSeconds(10));

    // when
    final long dueDate = interval.toEpochMilli(fromEpochMilli);

    // then — start date is in the past, so fromEpochMilli is returned unchanged
    assertThat(dueDate).isEqualTo(fromEpochMilli);
  }

  @Test
  public void shouldReturnStartDateWhenStartDateIsInTheFuture() {
    // given — start date is after fromEpochMilli
    final Instant now = Instant.now();
    final long fromEpochMilli = now.toEpochMilli();
    final Instant futureStart = now.plus(Duration.ofHours(1));
    final Interval interval =
        new Interval(
            Optional.of(ZonedDateTime.ofInstant(futureStart, ZoneId.systemDefault())),
            Period.ZERO,
            Duration.ofSeconds(10));

    // when
    final long dueDate = interval.toEpochMilli(fromEpochMilli);

    // then — must return the future start date unchanged
    assertThat(dueDate).isEqualTo(futureStart.toEpochMilli());
  }

  @Test
  public void shouldReturnFromEpochMilliPlusDurationWhenNoStartDatePresent() {
    // given — no start date, short duration
    final Interval interval = new Interval(Period.ZERO, Duration.ofSeconds(30));
    final long fromEpochMilli = Instant.now().toEpochMilli();

    // when
    final long dueDate = interval.toEpochMilli(fromEpochMilli);

    // then — due date is exactly fromEpochMilli + duration
    assertThat(dueDate).isEqualTo(fromEpochMilli + Duration.ofSeconds(30).toMillis());
  }

  @Test
  public void shouldReturnCalendarAdjustedDateWhenNoStartDatePresent() {
    // given — no start date, period-based interval
    final Interval interval = new Interval(Period.ofMonths(1), Duration.ZERO);
    final ZonedDateTime from = ZonedDateTime.of(2026, 1, 31, 0, 0, 0, 0, ZoneId.systemDefault());
    final long fromEpochMilli = from.toInstant().toEpochMilli();

    // when
    final long dueDate = interval.toEpochMilli(fromEpochMilli);

    // then — calendar arithmetic: Jan 31 + P1M = Feb 28, not Jan 31 + 31 days
    final long expected =
        ZonedDateTime.of(2026, 2, 28, 0, 0, 0, 0, ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    assertThat(dueDate).isEqualTo(expected);
  }

  @Test
  public void shouldKeepNaturalNextOccurrenceWhenNotOverdue() {
    // given — natural next occurrence (lastDueDate + interval) is still ahead of now
    final RepeatingInterval repeatingInterval =
        new RepeatingInterval(3, new Interval(Period.ZERO, Duration.ofSeconds(10)));
    final long lastDueDate = Instant.now().toEpochMilli();
    final long now = lastDueDate + Duration.ofSeconds(5).toMillis();

    // when
    final RepeatingInterval rescheduled =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, 3);

    // then — resolves to lastDueDate + interval, unaffected by now
    assertThat(rescheduled.getDueDate(now))
        .isEqualTo(lastDueDate + Duration.ofSeconds(10).toMillis());
    assertThat(rescheduled.getRepetitions()).isEqualTo(3);
  }

  @Test
  public void shouldReanchorOnNowWhenNaturalNextOccurrenceEqualsNow() {
    // given — now lands exactly on the natural next occurrence (lastDueDate + interval)
    final RepeatingInterval repeatingInterval =
        new RepeatingInterval(3, new Interval(Period.ZERO, Duration.ofSeconds(10)));
    final long lastDueDate = Instant.now().toEpochMilli();
    final long now = lastDueDate + Duration.ofSeconds(10).toMillis();

    // when
    final RepeatingInterval rescheduled =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, 3);

    // then — re-anchored a full interval past now, not the (already overdue) natural occurrence
    assertThat(rescheduled.getDueDate(now)).isEqualTo(now + Duration.ofSeconds(10).toMillis());
  }

  @Test
  public void shouldReanchorOnNowWhenOverdueForSeveralIntervals() {
    // given — several intervals elapsed since lastDueDate (e.g. a long suspension)
    final RepeatingInterval repeatingInterval =
        new RepeatingInterval(3, new Interval(Period.ZERO, Duration.ofSeconds(10)));
    final long lastDueDate = Instant.now().toEpochMilli();
    final long now = lastDueDate + Duration.ofSeconds(35).toMillis();

    // when
    final RepeatingInterval rescheduled =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, 3);

    // then — the missed cycles collapse into a single re-anchor a full interval past now
    assertThat(rescheduled.getDueDate(now)).isEqualTo(now + Duration.ofSeconds(10).toMillis());
  }

  @Test
  public void shouldCarryOverGivenRepetitionsRegardlessOfOriginalRepetitions() {
    // given — receiver has its own (stale) repetitions count
    final RepeatingInterval repeatingInterval =
        new RepeatingInterval(5, new Interval(Period.ZERO, Duration.ofSeconds(10)));
    final long lastDueDate = Instant.now().toEpochMilli();
    final long now = lastDueDate + Duration.ofSeconds(5).toMillis();

    // when
    final RepeatingInterval withFiniteRepetitions =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, 2);
    final RepeatingInterval withInfiniteRepetitions =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, RepeatingInterval.INFINITE);

    // then — the returned repetitions always reflect the passed-in value, not the receiver's
    assertThat(withFiniteRepetitions.getRepetitions()).isEqualTo(2);
    assertThat(withInfiniteRepetitions.getRepetitions()).isEqualTo(RepeatingInterval.INFINITE);
  }

  @Test
  public void shouldReplaceExistingStartTimeWithLastDueDate() {
    // given — receiver already carries an explicit start, unrelated to lastDueDate
    final ZonedDateTime originalStart = ZonedDateTime.now().minusDays(30);
    final RepeatingInterval repeatingInterval =
        new RepeatingInterval(
            3, new Interval(Optional.of(originalStart), Period.ZERO, Duration.ofSeconds(10)));
    final long lastDueDate = Instant.now().toEpochMilli();
    final long now = lastDueDate + Duration.ofSeconds(5).toMillis();

    // when
    final RepeatingInterval rescheduled =
        repeatingInterval.nextOccurrenceAfter(lastDueDate, now, 3);

    // then — rebased on lastDueDate, the receiver's original start is discarded
    assertThat(rescheduled.getDueDate(now))
        .isEqualTo(lastDueDate + Duration.ofSeconds(10).toMillis());
  }
}
