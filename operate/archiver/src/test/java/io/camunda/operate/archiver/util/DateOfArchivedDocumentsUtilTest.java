/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver.util;

import static io.camunda.operate.archiver.util.DateOfArchivedDocumentsUtil.getBucketStart;
import static io.camunda.operate.archiver.util.DateOfArchivedDocumentsUtil.getNextBucketStart;
import static io.camunda.operate.archiver.util.DateOfArchivedDocumentsUtil.validateRolloverConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DateOfArchivedDocumentsUtilTest {

  @ParameterizedTest(name = "getBucketStart(\"{0}\", \"{1}\", \"{2}\") = \"{3}\"")
  @MethodSource("bucketCases")
  void shouldReturnCorrectBucketStart(
      final String endDate,
      final String rolloverInterval,
      final String dateFormat,
      final String expectedBucketStart,
      final String expectedNextBucketStart) {
    assertThat(getBucketStart(endDate, rolloverInterval, dateFormat))
        .isEqualTo(expectedBucketStart);
  }

  @ParameterizedTest(name = "getNextBucketStart(\"{0}\", \"{1}\", \"{2}\") = \"{4}\"")
  @MethodSource("bucketCases")
  void shouldReturnCorrectNextBucketStart(
      final String endDate,
      final String rolloverInterval,
      final String dateFormat,
      final String expectedBucketStart,
      final String expectedNextBucketStart) {
    assertThat(getNextBucketStart(endDate, rolloverInterval, dateFormat))
        .isEqualTo(expectedNextBucketStart);
  }

  // Each case asserts BOTH the bucket start and the (exclusive) next bucket start, where
  // nextBucketStart = bucketStart + rolloverInterval, rendered with the same dateFormat.
  private static Stream<Arguments> bucketCases() {
    return Stream.of(
        // endDate, interval, format, expectedBucketStart, expectedNextBucketStart

        // -- DAYS interval --
        Arguments.of("2026-03-16", "1d", "date", "2026-03-16", "2026-03-17"),
        // 3d: March 16 falls in the March 14 bucket
        Arguments.of("2026-03-16", "3d", "date", "2026-03-14", "2026-03-17"),
        // 3d: exactly on a bucket boundary
        Arguments.of("2026-03-14", "3d", "date", "2026-03-14", "2026-03-17"),
        // 7d: weekly-equivalent via days
        Arguments.of("2026-03-18", "7d", "date", "2026-03-12", "2026-03-19"),

        // -- WEEKS interval (converted to days internally) --
        Arguments.of("2026-03-18", "1w", "date", "2026-03-12", "2026-03-19"),
        Arguments.of("2026-03-18", "2w", "date", "2026-03-12", "2026-03-26"),

        // -- Sub-day intervals with a day-only format: bucket start AND next both collapse to the
        //    day, so next == start. This is the documented limitation (use a date-time format for
        //    sub-day rollover intervals). --
        Arguments.of("2026-03-16", "1h", "date", "2026-03-16", "2026-03-16"),
        Arguments.of("2026-03-16", "6h", "date", "2026-03-16", "2026-03-16"),
        Arguments.of("2026-03-16", "30m", "date", "2026-03-16", "2026-03-16"),
        Arguments.of("2026-03-16", "30s", "date", "2026-03-16", "2026-03-16"),

        // -- MONTHS interval --
        Arguments.of("2026-03-16", "1M", "date", "2026-03-01", "2026-04-01"),
        // first day of month is on boundary
        Arguments.of("2026-03-01", "1M", "date", "2026-03-01", "2026-04-01"),
        // 2M: bi-monthly buckets from epoch (Jan 1970)
        Arguments.of("2026-03-16", "2M", "date", "2026-03-01", "2026-05-01"),
        // 2M: April falls in the same bi-monthly bucket as March
        Arguments.of("2026-04-15", "2M", "date", "2026-03-01", "2026-05-01"),
        // 3M: quarterly buckets; March 2026 => quarter starting Jan 2026
        Arguments.of("2026-03-16", "3M", "date", "2026-01-01", "2026-04-01"),
        // 3M: April in the next quarter bucket
        Arguments.of("2026-04-16", "3M", "date", "2026-04-01", "2026-07-01"),
        // 6M: semi-annual; March 2026 => half starting Jan 2026
        Arguments.of("2026-03-16", "6M", "date", "2026-01-01", "2026-07-01"),
        // 6M: July falls in the second-half bucket, next rolls into the following year
        Arguments.of("2026-07-15", "6M", "date", "2026-07-01", "2027-01-01"),

        // -- Year boundary crossing --
        Arguments.of("2026-01-01", "1d", "date", "2026-01-01", "2026-01-02"),
        Arguments.of("2026-01-01", "3d", "date", "2026-01-01", "2026-01-04"),
        Arguments.of("2026-01-15", "1M", "date", "2026-01-01", "2026-02-01"),

        // -- Epoch date --
        Arguments.of("1970-01-01", "1d", "date", "1970-01-01", "1970-01-02"),
        Arguments.of("1970-01-01", "1M", "date", "1970-01-01", "1970-02-01"),

        // -- Custom date format: explicit "yyyy-MM-dd" pattern --
        Arguments.of("2026-03-16", "1d", "yyyy-MM-dd", "2026-03-16", "2026-03-17"),
        Arguments.of("2026-03-16", "3d", "yyyy-MM-dd", "2026-03-14", "2026-03-17"),
        Arguments.of("2026-03-16", "1M", "yyyy-MM-dd", "2026-03-01", "2026-04-01"),

        // -- Custom date format with hours: "yyyy-MM-dd-HH" --
        Arguments.of("2026-03-16-14", "1h", "yyyy-MM-dd-HH", "2026-03-16-14", "2026-03-16-15"),
        Arguments.of("2026-03-16-15", "2h", "yyyy-MM-dd-HH", "2026-03-16-14", "2026-03-16-16"),
        Arguments.of("2026-03-16-14", "6h", "yyyy-MM-dd-HH", "2026-03-16-12", "2026-03-16-18"),
        Arguments.of("2026-03-16-14", "1d", "yyyy-MM-dd-HH", "2026-03-16-00", "2026-03-17-00"),
        Arguments.of("2026-03-16-14", "3d", "yyyy-MM-dd-HH", "2026-03-14-00", "2026-03-17-00"),

        // -- Custom date format with minutes: "yyyy-MM-dd-HH-mm" --
        Arguments.of(
            "2026-03-16-14-45", "1m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-45", "2026-03-16-14-46"),
        Arguments.of(
            "2026-03-16-14-45", "30m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-30", "2026-03-16-15-00"),
        Arguments.of(
            "2026-03-16-14-15", "30m", "yyyy-MM-dd-HH-mm", "2026-03-16-14-00", "2026-03-16-14-30"),
        Arguments.of(
            "2026-03-16-14-45", "1h", "yyyy-MM-dd-HH-mm", "2026-03-16-14-00", "2026-03-16-15-00"),
        Arguments.of(
            "2026-03-16-14-45", "1d", "yyyy-MM-dd-HH-mm", "2026-03-16-00-00", "2026-03-17-00-00"),

        // -- Custom date format with seconds: "yyyy-MM-dd-HH-mm-ss" --
        Arguments.of(
            "2026-03-16-14-30-45",
            "1s",
            "yyyy-MM-dd-HH-mm-ss",
            "2026-03-16-14-30-45",
            "2026-03-16-14-30-46"),
        Arguments.of(
            "2026-03-16-14-30-45",
            "30s",
            "yyyy-MM-dd-HH-mm-ss",
            "2026-03-16-14-30-30",
            "2026-03-16-14-31-00"),
        Arguments.of(
            "2026-03-16-14-30-15",
            "30s",
            "yyyy-MM-dd-HH-mm-ss",
            "2026-03-16-14-30-00",
            "2026-03-16-14-30-30"),
        Arguments.of(
            "2026-03-16-14-30-45",
            "1m",
            "yyyy-MM-dd-HH-mm-ss",
            "2026-03-16-14-30-00",
            "2026-03-16-14-31-00"),
        Arguments.of(
            "2026-03-16-14-30-45",
            "1h",
            "yyyy-MM-dd-HH-mm-ss",
            "2026-03-16-14-00-00",
            "2026-03-16-15-00-00"));
  }

  @Test
  void shouldThrowOnInvalidRolloverInterval() {
    assertThatThrownBy(() -> getBucketStart("2026-03-16", "1x", "date"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> getNextBucketStart("2026-03-16", "1x", "date"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldThrowOnInvalidDateString() {
    assertThatThrownBy(() -> getBucketStart("not-a-date", "1d", "yyyy-MM-dd'T'HH:mm:ss"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> getNextBucketStart("not-a-date", "1d", "yyyy-MM-dd'T'HH:mm:ss"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "validateRolloverConfiguration(\"{0}\", \"{1}\") rejected")
  @MethodSource("tooCoarseConfigurations")
  void shouldRejectDateFormatCoarserThanInterval(
      final String rolloverInterval, final String dateFormat) {
    assertThatThrownBy(() -> validateRolloverConfiguration(rolloverInterval, dateFormat))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<Arguments> tooCoarseConfigurations() {
    return Stream.of(
        // sub-day interval with a day-only format
        Arguments.of("4h", "date"),
        Arguments.of("30m", "date"),
        Arguments.of("30s", "yyyy-MM-dd"),
        // interval finer than the format's finest field
        Arguments.of("1m", "yyyy-MM-dd-HH"),
        Arguments.of("30s", "yyyy-MM-dd-HH-mm"),
        // day/week interval with a month-only format
        Arguments.of("1d", "yyyy-MM"),
        Arguments.of("1w", "yyyy-MM"),
        // month interval with a year-only format
        Arguments.of("1M", "yyyy"));
  }

  @ParameterizedTest(name = "validateRolloverConfiguration(\"{0}\", \"{1}\") accepted")
  @MethodSource("fineEnoughConfigurations")
  void shouldAcceptDateFormatAtLeastAsFineAsInterval(
      final String rolloverInterval, final String dateFormat) {
    assertThatCode(() -> validateRolloverConfiguration(rolloverInterval, dateFormat))
        .doesNotThrowAnyException();
  }

  private static Stream<Arguments> fineEnoughConfigurations() {
    return Stream.of(
        // default configuration
        Arguments.of("1d", "date"),
        Arguments.of("1d", "yyyy-MM-dd"),
        // coarser interval than the format is always fine
        Arguments.of("1M", "date"),
        Arguments.of("2d", "date"),
        Arguments.of("1w", "date"),
        // format granularity equal to the interval
        Arguments.of("4h", "yyyy-MM-dd-HH"),
        Arguments.of("30m", "yyyy-MM-dd-HH-mm"),
        Arguments.of("30s", "yyyy-MM-dd'T'HH:mm:ss"),
        // format finer than the interval
        Arguments.of("1h", "yyyy-MM-dd-HH-mm"));
  }
}
