/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.junit.Test;

public class CronTimerTest {

  @Test
  public void shouldFailToParseIfWrongExpression() {
    assertThatThrownBy(() -> CronTimer.parse(null)).isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("")).isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("*")).isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * * *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * * * * *"))
        .isInstanceOf(DateTimeParseException.class);

    // second range [0, 59]
    assertThatThrownBy(() -> CronTimer.parse("60 * * * * *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("20-10 * * * * *"))
        .isInstanceOf(DateTimeParseException.class);

    // minute range [0, 59]
    assertThatThrownBy(() -> CronTimer.parse("* 60 * * * *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* 20-10 * * * *"))
        .isInstanceOf(DateTimeParseException.class);

    // hour range [0, 23]
    assertThatThrownBy(() -> CronTimer.parse("* * 24 * * *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * 20-10 * * *"))
        .isInstanceOf(DateTimeParseException.class);

    // day range [1, 31]
    assertThatThrownBy(() -> CronTimer.parse("* * * 0 * *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * 32 * *"))
        .isInstanceOf(DateTimeParseException.class);

    // month range [1, 12]
    assertThatThrownBy(() -> CronTimer.parse("* * * * 0 *"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * * 13 *"))
        .isInstanceOf(DateTimeParseException.class);

    // week range (0 or 7 is Sunday, or MON-SUN)
    assertThatThrownBy(() -> CronTimer.parse("* * * * * 8"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * * * *SUN"))
        .isInstanceOf(DateTimeParseException.class);
    assertThatThrownBy(() -> CronTimer.parse("* * * * * SUN*"))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  public void shouldParseWithCronExpression() {
    // given
    final String text = "*/10 * * * * *";
    final long now = System.currentTimeMillis();
    final long expected = now + 10_000L;

    // when
    final CronTimer timer = CronTimer.parse(text);
    final long dueDate = timer.getDueDate(now);

    // then
    assertThat(dueDate).isBetween(now, expected);
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldMatchAll() {
    // given
    final String text = "* * * * * *";
    final ZonedDateTime last = ZonedDateTime.now(ZoneId.systemDefault());
    final long expected = last.plusSeconds(1).toInstant().toEpochMilli();

    // when
    final CronTimer timer = CronTimer.parse(text);
    final long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected);
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldMatchLastSecond() {
    // given
    final String text = "* * * * * *";
    final ZonedDateTime last = ZonedDateTime.now(ZoneId.systemDefault()).withSecond(58);
    final long expected = last.plusSeconds(1).toInstant().toEpochMilli();

    // when
    final CronTimer timer = CronTimer.parse(text);
    final long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected);
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldMatchSpecificSecond() {
    // given
    final String text = "10 * * * * *";
    final ZonedDateTime last = ZonedDateTime.now(ZoneId.systemDefault()).withSecond(9);
    final long expected = last.withSecond(10).withNano(0).toInstant().toEpochMilli();

    // when
    final CronTimer timer = CronTimer.parse(text);
    final long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected);
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseSecondRange() {
    final String text = "10-15 * * * * *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    for (int i = 9; i < 15; i++) {
      final ZonedDateTime last = now.withSecond(i);
      final long expected = last.plusSeconds(1).toInstant().toEpochMilli();
      assertThat(timer.getDueDate(last.toInstant().toEpochMilli())).isEqualTo(expected);
      assertThat(timer.getRepetitions()).isEqualTo(-1);
    }
  }

  @Test
  public void shouldParseSpecificMinuteSecond() {
    // given
    final String text = "55 5 * * * *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime last =
        ZonedDateTime.now(ZoneId.systemDefault()).withMinute(4).withSecond(54);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    final ZonedDateTime expected = last.plusMinutes(1).withSecond(55).withNano(0);

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.plusHours(1).toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseSpecificHourSecond() {
    // given
    final String text = "55 * 10 * * *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime last = ZonedDateTime.now(ZoneId.systemDefault()).withHour(9).withSecond(54);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    final ZonedDateTime expected = last.plusHours(1).withMinute(0).withSecond(55).withNano(0);

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.plusMinutes(1).toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseSpecificMinuteHour() {
    // given
    final String text = "* 5 10 * * *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime last = ZonedDateTime.now(ZoneId.systemDefault()).withHour(9).withMinute(4);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    final ZonedDateTime expected = last.plusHours(1).plusMinutes(1).withSecond(0).withNano(0);

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.plusSeconds(1).toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseSpecificDayOfMonthSecond() {
    // given
    final String text = "55 * * 3 * *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime last =
        ZonedDateTime.now(ZoneId.systemDefault()).withDayOfMonth(2).withSecond(54);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    final ZonedDateTime expected =
        last.plusDays(1).withHour(0).withMinute(0).withSecond(55).withNano(0);

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.plusMinutes(1).toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseSpecificDate() {
    // given
    final String text = "* * * 3 11 *";
    final CronTimer timer = CronTimer.parse(text);
    final ZonedDateTime last =
        ZonedDateTime.now(ZoneId.systemDefault()).withMonth(10).withDayOfMonth(2);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    final ZonedDateTime expected =
        last.of(last.getYear(), 11, 3, 0, 0, 0, 0, ZoneId.systemDefault());

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.plusSeconds(1).toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseNonExistentSpecificDate() {
    // given
    final String text = "0 0 0 31 6 *";
    final ZonedDateTime last =
        ZonedDateTime.now(ZoneId.systemDefault()).withMonth(3).withDayOfMonth(10);

    // when
    final CronTimer timer = CronTimer.parse(text);
    final long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(last.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(0);
  }

  @Test
  public void shouldParseQuartzLastDayOfMonthEveryHour() {
    // given
    final String text = "0 0 * L * *";
    final CronTimer timer = CronTimer.parse(text);
    ZonedDateTime last = ZonedDateTime.of(2022, 1, 30, 0, 1, 0, 0, ZoneId.systemDefault());
    ZonedDateTime expected = ZonedDateTime.of(2022, 1, 31, 0, 0, 0, 0, ZoneId.systemDefault());

    // when
    long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    last = ZonedDateTime.of(2022, 1, 31, 1, 0, 0, 0, ZoneId.systemDefault());
    expected = ZonedDateTime.of(2022, 1, 31, 2, 0, 0, 0, ZoneId.systemDefault());
    dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseQuartzLastDayOfMonthOffset() {
    // L-3 =  third-to-last day of the month
    final String text = "0 0 0 L-3 * *";
    final CronTimer timer = CronTimer.parse(text);

    final ZonedDateTime last =
        ZonedDateTime.now(ZoneId.systemDefault()).withYear(2022).withMonth(1).withDayOfMonth(10);
    long fromEpochMilli = last.toInstant().toEpochMilli();
    ZonedDateTime expected =
        ZonedDateTime.now(ZoneId.systemDefault())
            .withYear(2022)
            .withMonth(1)
            .withDayOfMonth(28)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);

    // when
    long dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    expected =
        ZonedDateTime.now(ZoneId.systemDefault())
            .withYear(2022)
            .withMonth(2)
            .withDayOfMonth(25)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    expected =
        ZonedDateTime.now(ZoneId.systemDefault())
            .withYear(2022)
            .withMonth(3)
            .withDayOfMonth(28)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    fromEpochMilli = dueDate;
    expected =
        ZonedDateTime.now(ZoneId.systemDefault())
            .withYear(2022)
            .withMonth(4)
            .withDayOfMonth(27)
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0);
    dueDate = timer.getDueDate(fromEpochMilli);

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseQuartzLastDayOfMonthOffsetEveryHour() {
    // given
    final String text = "0 0 * L-1 * *";
    final CronTimer timer = CronTimer.parse(text);
    ZonedDateTime last = ZonedDateTime.of(2022, 1, 29, 0, 1, 0, 0, ZoneId.systemDefault());
    ZonedDateTime expected = ZonedDateTime.of(2022, 1, 30, 0, 0, 0, 0, ZoneId.systemDefault());

    // when
    long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    last = ZonedDateTime.of(2022, 1, 30, 1, 0, 0, 0, ZoneId.systemDefault());
    expected = ZonedDateTime.of(2022, 1, 30, 2, 0, 0, 0, ZoneId.systemDefault());
    dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseQuartzFirstWeekdayOfMonthEveryHour() {
    // given
    final String text = "0 0 * 1W * *";
    final CronTimer timer = CronTimer.parse(text);
    ZonedDateTime last = ZonedDateTime.of(2022, 6, 29, 0, 1, 0, 0, ZoneId.systemDefault());
    ZonedDateTime expected = ZonedDateTime.of(2022, 7, 1, 0, 0, 0, 0, ZoneId.systemDefault());

    // when
    long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    last = ZonedDateTime.of(2022, 7, 1, 1, 0, 0, 0, ZoneId.systemDefault());
    expected = ZonedDateTime.of(2022, 7, 1, 2, 0, 0, 0, ZoneId.systemDefault());
    dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }

  @Test
  public void shouldParseQuartzLastWeekdayOfMonthEveryHour() {
    // given
    final String text = "0 0 * LW * *";
    final CronTimer timer = CronTimer.parse(text);
    ZonedDateTime last = ZonedDateTime.of(2022, 6, 29, 0, 1, 0, 0, ZoneId.systemDefault());
    ZonedDateTime expected = ZonedDateTime.of(2022, 6, 30, 0, 0, 0, 0, ZoneId.systemDefault());

    // when
    long dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);

    // when
    last = ZonedDateTime.of(2022, 6, 30, 1, 0, 0, 0, ZoneId.systemDefault());
    expected = ZonedDateTime.of(2022, 6, 30, 2, 0, 0, 0, ZoneId.systemDefault());
    dueDate = timer.getDueDate(last.toInstant().toEpochMilli());

    // then
    assertThat(dueDate).isEqualTo(expected.toInstant().toEpochMilli());
    assertThat(timer.getRepetitions()).isEqualTo(-1);
  }
}
